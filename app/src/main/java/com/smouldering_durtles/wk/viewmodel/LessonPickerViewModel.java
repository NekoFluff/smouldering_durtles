package com.smouldering_durtles.wk.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smouldering_durtles.wk.db.model.Subject;
import com.smouldering_durtles.wk.enums.SubjectType;
import com.smouldering_durtles.wk.livedata.LiveTimeLine;
import com.smouldering_durtles.wk.model.TimeLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public final class LessonPickerViewModel extends AndroidViewModel {

    // ---- Data model ----

    public static final class TypeGroup {
        public final SubjectType type;
        public final String tag;
        public final List<Subject> subjects;

        TypeGroup(final SubjectType type, final String tag, final List<Subject> subjects) {
            this.type = type;
            this.tag = tag;
            this.subjects = Collections.unmodifiableList(subjects);
        }
    }

    public static final class LevelGroup {
        public final int level;
        public final List<TypeGroup> typeGroups;

        LevelGroup(final int level, final List<TypeGroup> typeGroups) {
            this.level = level;
            this.typeGroups = Collections.unmodifiableList(typeGroups);
        }
    }

    // ---- State ----

    private final MutableLiveData<List<LevelGroup>> levelGroupsLiveData =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<Long>> selectedIdsLiveData =
            new MutableLiveData<>(new HashSet<>());

    private List<Subject> allSubjects = Collections.emptyList();
    /** tag → subject IDs in that section, for select-group and isGroupFullySelected */
    private final Map<String, List<Long>> sectionSubjectIds = new LinkedHashMap<>();
    /** level → list of section tags for that level, for select-level */
    private final Map<Integer, List<String>> levelTags = new HashMap<>();

    public LessonPickerViewModel(final Application application) {
        super(application);
        loadItems();
    }

    public LiveData<List<LevelGroup>> getLevelGroups() {
        return levelGroupsLiveData;
    }

    public LiveData<Set<Long>> getSelectedIds() {
        return selectedIdsLiveData;
    }

    private void loadItems() {
        final @Nullable TimeLine timeLine = LiveTimeLine.getInstance().get();
        if (timeLine == null) {
            return;
        }
        final List<Subject> subjects = new ArrayList<>(timeLine.getAvailableLessons());
        subjects.sort(Comparator.comparingInt(Subject::getLevel)
                .thenComparingInt(Subject::getTypeOrder));

        allSubjects = Collections.unmodifiableList(subjects);
        sectionSubjectIds.clear();
        levelTags.clear();

        final List<LevelGroup> levelGroups = new ArrayList<>();
        int currentLevel = -1;
        String currentTag = null;
        List<TypeGroup> currentTypeGroups = new ArrayList<>();
        List<Subject> currentTypeSubjects = new ArrayList<>();
        SubjectType currentType = null;

        for (final Subject subject : subjects) {
            final int level = subject.getLevel();
            final SubjectType type = subject.getType();
            final String tag = sectionTag(level, type);

            if (level != currentLevel) {
                if (currentLevel != -1) {
                    flushTypeGroup(currentTag, currentTypeSubjects, currentType, currentTypeGroups, currentLevel);
                    levelGroups.add(new LevelGroup(currentLevel, currentTypeGroups));
                }
                currentLevel = level;
                currentType = type;
                currentTag = tag;
                currentTypeGroups = new ArrayList<>();
                currentTypeSubjects = new ArrayList<>();
                levelTags.put(level, new ArrayList<>());
            } else if (type != currentType) {
                flushTypeGroup(currentTag, currentTypeSubjects, currentType, currentTypeGroups, currentLevel);
                currentType = type;
                currentTag = tag;
                currentTypeSubjects = new ArrayList<>();
            }
            currentTypeSubjects.add(subject);
        }

        if (currentLevel != -1) {
            flushTypeGroup(currentTag, currentTypeSubjects, currentType, currentTypeGroups, currentLevel);
            levelGroups.add(new LevelGroup(currentLevel, currentTypeGroups));
        }

        levelGroupsLiveData.setValue(levelGroups);
    }

    private void flushTypeGroup(final String tag, final List<Subject> subjects,
                                final SubjectType type, final List<TypeGroup> into, final int level) {
        if (subjects.isEmpty() || type == null) return;
        final List<Long> ids = new ArrayList<>();
        for (final Subject s : subjects) ids.add(s.getId());
        sectionSubjectIds.put(tag, ids);
        levelTags.computeIfAbsent(level, k -> new ArrayList<>()).add(tag);
        into.add(new TypeGroup(type, tag, new ArrayList<>(subjects)));
    }

    // ---- Selection mutations ----

    public void toggleSelection(final long subjectId) {
        final Set<Long> current = mutableCopy(selectedIdsLiveData.getValue());
        if (!current.remove(subjectId)) {
            current.add(subjectId);
        }
        selectedIdsLiveData.setValue(current);
    }

    public void selectGroup(final String tag, final boolean select) {
        final Set<Long> current = mutableCopy(selectedIdsLiveData.getValue());
        final List<Long> ids = sectionSubjectIds.getOrDefault(tag, Collections.emptyList());
        if (select) current.addAll(ids); else current.removeAll(ids);
        selectedIdsLiveData.setValue(current);
    }

    public void selectLevel(final int level, final boolean select) {
        final Set<Long> current = mutableCopy(selectedIdsLiveData.getValue());
        final List<String> tags = levelTags.getOrDefault(level, Collections.emptyList());
        for (final String tag : tags) {
            final List<Long> ids = sectionSubjectIds.getOrDefault(tag, Collections.emptyList());
            if (select) current.addAll(ids); else current.removeAll(ids);
        }
        selectedIdsLiveData.setValue(current);
    }

    public void selectAll(final boolean select) {
        final Set<Long> next = new HashSet<>();
        if (select) {
            for (final Subject s : allSubjects) next.add(s.getId());
        }
        selectedIdsLiveData.setValue(next);
    }

    // ---- Queries ----

    public List<Subject> getSelectedSubjects() {
        final Set<Long> selected = getOrEmpty(selectedIdsLiveData.getValue());
        final List<Subject> result = new ArrayList<>();
        for (final Subject s : allSubjects) {
            if (selected.contains(s.getId())) result.add(s);
        }
        Collections.shuffle(result);
        return result;
    }

    public boolean isGroupFullySelected(final String tag) {
        final Set<Long> selected = getOrEmpty(selectedIdsLiveData.getValue());
        final List<Long> ids = sectionSubjectIds.getOrDefault(tag, Collections.emptyList());
        return !ids.isEmpty() && selected.containsAll(ids);
    }

    public boolean isLevelFullySelected(final int level) {
        final List<String> tags = levelTags.getOrDefault(level, Collections.emptyList());
        return !tags.isEmpty() && tags.stream().allMatch(this::isGroupFullySelected);
    }

    public int getTotalCount() {
        return allSubjects.size();
    }

    // ---- Helpers ----

    public static String sectionTag(final int level, final SubjectType type) {
        return String.format(Locale.ROOT, "%d_%s", level, type.name());
    }

    public static String typeLabel(final SubjectType type) {
        if (type == SubjectType.WANIKANI_RADICAL) return "Radicals";
        if (type == SubjectType.WANIKANI_KANJI) return "Kanji";
        if (type == SubjectType.WANIKANI_KANA_VOCAB) return "Kana Vocabulary";
        return "Vocabulary";
    }

    private static Set<Long> mutableCopy(final @Nullable Set<Long> src) {
        return src != null ? new HashSet<>(src) : new HashSet<>();
    }

    private static Set<Long> getOrEmpty(final @Nullable Set<Long> set) {
        return set != null ? set : Collections.emptySet();
    }
}
