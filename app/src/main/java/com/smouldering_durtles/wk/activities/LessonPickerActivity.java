package com.smouldering_durtles.wk.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.db.model.Subject;
import com.smouldering_durtles.wk.model.Session;
import com.smouldering_durtles.wk.viewmodel.LessonPickerViewModel;
import com.smouldering_durtles.wk.viewmodel.LessonPickerViewModel.LevelGroup;
import com.smouldering_durtles.wk.viewmodel.LessonPickerViewModel.TypeGroup;
import com.smouldering_durtles.wk.views.FlowLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import static com.smouldering_durtles.wk.util.ObjectSupport.runAsync;
import static com.smouldering_durtles.wk.util.ObjectSupport.safe;

/**
 * Activity that lets the user hand-pick which available lesson items to study,
 * then starts a shuffled lesson session with only those items.
 *
 * <p>Items are displayed as chips grouped by level then type. Tapping a chip
 * toggles its selection state. Unselected chips show a gray border on a white
 * background; selected chips show a filled background — both with black text
 * for readability on e-ink/B&W displays.</p>
 */
public final class LessonPickerActivity extends AbstractActivity {

    private static final class ChipState {
        final View view;
        final TextView characterView;
        final TextView readingView;
        final GradientDrawable selectedBg;
        final GradientDrawable unselectedBg;

        ChipState(final View view, final TextView characterView, final TextView readingView,
                  final GradientDrawable selectedBg, final GradientDrawable unselectedBg) {
            this.view = view;
            this.characterView = characterView;
            this.readingView = readingView;
            this.selectedBg = selectedBg;
            this.unselectedBg = unselectedBg;
        }
    }

    private LessonPickerViewModel viewModel;

    private LinearLayout contentContainer;
    private Button startButton;
    private Button selectAllButton;
    private TextView emptyStateText;

    private final Map<Long, ChipState> chipStateById = new HashMap<>();
    private final Map<Integer, TextView> levelSelectAllViews = new HashMap<>();
    private final Map<String, TextView> typeSelectAllViews = new HashMap<>();

    public LessonPickerActivity() {
        super(R.layout.activity_lesson_picker, R.menu.generic_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(LessonPickerViewModel.class);

        emptyStateText = findViewById(R.id.emptyStateText);
        startButton = findViewById(R.id.startButton);
        selectAllButton = findViewById(R.id.selectAllButton);
        contentContainer = findViewById(R.id.contentContainer);

        viewModel.getLevelGroups().observe(this, levelGroups -> safe(() ->
                buildChipLayout(levelGroups)));

        viewModel.getSelectedIds().observe(this, selected -> safe(() ->
                updateSelectionVisuals(selected)));

        selectAllButton.setOnClickListener(v -> safe(() -> {
            final Set<Long> ids = viewModel.getSelectedIds().getValue();
            final boolean allSelected = ids != null && ids.size() == viewModel.getTotalCount();
            viewModel.selectAll(!allSelected);
        }));

        startButton.setOnClickListener(v -> safe(() -> {
            if (!interactionEnabled) return;
            disableInteraction();

            final List<Subject> selected = viewModel.getSelectedSubjects();
            if (selected.isEmpty()) {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
                enableInteraction();
                return;
            }

            runAsync(this, () -> {
                Session.getInstance().startNewPickedLessonSession(selected);
                return null;
            }, result -> goToActivity(SessionActivity.class));
        }));
    }

    private void buildChipLayout(final @Nullable List<LevelGroup> levelGroups) {
        contentContainer.removeAllViews();
        chipStateById.clear();
        levelSelectAllViews.clear();
        typeSelectAllViews.clear();

        if (levelGroups == null || levelGroups.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
            return;
        }

        emptyStateText.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);

        final LayoutInflater inflater = LayoutInflater.from(this);
        final float cornerRadius = dpToPx(8);
        final int strokeWidth = Math.round(dpToPx(1.5f));

        for (final LevelGroup levelGroup : levelGroups) {
            // Card container for this level group
            final LinearLayout card = makeCardContainer();
            contentContainer.addView(card);

            // Level header
            final View levelHeaderView = inflater.inflate(R.layout.lesson_picker_level_header, card, false);
            final TextView levelLabel = levelHeaderView.findViewById(R.id.levelLabel);
            final TextView levelCount = levelHeaderView.findViewById(R.id.levelCount);
            final TextView levelSelectAll = levelHeaderView.findViewById(R.id.levelSelectAll);

            final int totalInLevel = levelGroup.typeGroups.stream()
                    .mapToInt(tg -> tg.subjects.size()).sum();
            levelLabel.setText(String.format(Locale.ROOT, "Level %d", levelGroup.level));
            levelCount.setText(String.format(Locale.ROOT, "· %d items", totalInLevel));
            levelSelectAll.setOnClickListener(v -> safe(() -> {
                final boolean allSelected = viewModel.isLevelFullySelected(levelGroup.level);
                viewModel.selectLevel(levelGroup.level, !allSelected);
            }));
            levelSelectAllViews.put(levelGroup.level, levelSelectAll);
            card.addView(levelHeaderView);

            // Horizontal divider under level header
            final View divider = new View(this);
            final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
            dividerParams.setMarginStart(dpToPx(12));
            dividerParams.setMarginEnd(dpToPx(12));
            divider.setLayoutParams(dividerParams);
            divider.setBackgroundColor(0x1A000000); // 10% black
            card.addView(divider);

            for (final TypeGroup typeGroup : levelGroup.typeGroups) {
                // Type sub-header
                final View typeHeaderView = inflater.inflate(R.layout.lesson_picker_type_header, card, false);
                final TextView typeLabel = typeHeaderView.findViewById(R.id.typeLabel);
                final TextView typeSelectAll = typeHeaderView.findViewById(R.id.typeSelectAll);
                typeLabel.setText(LessonPickerViewModel.typeLabel(typeGroup.type));
                typeSelectAll.setOnClickListener(v -> safe(() -> {
                    final boolean allSelected = viewModel.isGroupFullySelected(typeGroup.tag);
                    viewModel.selectGroup(typeGroup.tag, !allSelected);
                }));
                typeSelectAllViews.put(typeGroup.tag, typeSelectAll);
                card.addView(typeHeaderView);

                // Chip flow
                final FlowLayout flowLayout = new FlowLayout(this);
                final LinearLayout.LayoutParams flowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                flowParams.setMarginStart(dpToPx(4));
                flowParams.setMarginEnd(dpToPx(4));
                flowParams.topMargin = dpToPx(4);
                flowParams.bottomMargin = dpToPx(8);
                flowLayout.setLayoutParams(flowParams);

                for (final Subject subject : typeGroup.subjects) {
                    final int typeColor = subject.getType().getBackgroundColor();

                    final GradientDrawable selectedBg = new GradientDrawable();
                    selectedBg.setColor(typeColor);
                    selectedBg.setCornerRadius(cornerRadius);

                    final GradientDrawable unselectedBg = new GradientDrawable();
                    unselectedBg.setColor(Color.WHITE);
                    unselectedBg.setCornerRadius(cornerRadius);
                    unselectedBg.setStroke(strokeWidth, Color.LTGRAY);

                    final View chip = inflater.inflate(R.layout.lesson_picker_chip, flowLayout, false);
                    final TextView characterView = chip.findViewById(R.id.chipCharacter);
                    final TextView readingView = chip.findViewById(R.id.chipReading);

                    final @Nullable String chars = subject.getCharacters();
                    characterView.setText(chars != null && !chars.isEmpty() ? chars : subject.getSlug());

                    final String reading = subject.getOneReading();
                    readingView.setText(reading.isEmpty() ? subject.getOneMeaning() : reading);

                    chip.setBackground(unselectedBg);
                    chip.setOnClickListener(vv -> safe(() -> viewModel.toggleSelection(subject.getId())));

                    chipStateById.put(subject.getId(),
                            new ChipState(chip, characterView, readingView, selectedBg, unselectedBg));
                    flowLayout.addView(chip);
                }
                card.addView(flowLayout);
            }
        }

        // Apply current selection state to freshly built chips
        final @Nullable Set<Long> currentSelected = viewModel.getSelectedIds().getValue();
        if (currentSelected != null) {
            updateSelectionVisuals(currentSelected);
        }
    }

    private LinearLayout makeCardContainer() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), 0);
        card.setLayoutParams(params);

        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dpToPx(8));
        cardBg.setStroke(dpToPx(1), 0x1A000000); // subtle 10% black border
        card.setBackground(cardBg);

        return card;
    }

    private void updateSelectionVisuals(final Set<Long> selected) {
        // Update chip backgrounds and text colors
        for (final Map.Entry<Long, ChipState> entry : chipStateById.entrySet()) {
            final ChipState state = entry.getValue();
            final boolean isSelected = selected.contains(entry.getKey());
            state.view.setBackground(isSelected ? state.selectedBg : state.unselectedBg);
            final int textColor = isSelected ? Color.WHITE : Color.BLACK;
            state.characterView.setTextColor(textColor);
            state.readingView.setTextColor(textColor);
        }

        // Update level "Select All" / "Deselect All" labels
        for (final Map.Entry<Integer, TextView> entry : levelSelectAllViews.entrySet()) {
            entry.getValue().setText(
                    viewModel.isLevelFullySelected(entry.getKey()) ? "Deselect All" : "Select All");
        }

        // Update type "Select All" / "Deselect All" labels
        for (final Map.Entry<String, TextView> entry : typeSelectAllViews.entrySet()) {
            entry.getValue().setText(
                    viewModel.isGroupFullySelected(entry.getKey()) ? "Deselect All" : "Select All");
        }

        // Bottom bar
        final int count = selected.size();
        final int total = viewModel.getTotalCount();
        startButton.setText(String.format(Locale.ROOT, "Start (%d / %d)", count, total));
        startButton.setEnabled(count > 0 && interactionEnabled);
        selectAllButton.setText(count == total && total > 0 ? "Deselect all" : "Select all");
    }

    private int dpToPx(final float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResumeLocal() {
        if (!Session.getInstance().isInactive()) {
            finish();
        }
    }

    @Override
    protected void onPauseLocal() {
        //
    }

    @Override
    protected void enableInteractionLocal() {
        final @Nullable Set<Long> ids = viewModel.getSelectedIds().getValue();
        startButton.setEnabled(ids != null && !ids.isEmpty());
    }

    @Override
    protected void disableInteractionLocal() {
        startButton.setEnabled(false);
    }

    @Override
    protected boolean showWithoutApiKey() {
        return false;
    }
}
