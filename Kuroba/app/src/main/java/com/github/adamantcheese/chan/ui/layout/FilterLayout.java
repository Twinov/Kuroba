/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.layout;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.COLOR;
import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.WATCH;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.*;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.features.html_styling.StyledHtml;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.ui.text.spans.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.view.*;
import com.github.adamantcheese.chan.ui.widget.ColorPickerView;
import com.github.adamantcheese.chan.utils.DefaultOnSeekBarChangeListener;

import java.util.*;
import java.util.regex.Pattern;

import javax.inject.Inject;

public class FilterLayout
        extends LinearLayout {
    private EditText label;
    private TextView typeText;
    private TextView boardsSelector;
    private TextView pattern;
    private TextView negativePattern;
    private TextView patternPreview;
    private ImageView patternPreviewStatus;
    private TextView actionText;
    private ImageView colorPreview;
    private CheckBox applyToReplies;
    private CheckBox onlyOnOP;
    private CheckBox applyToSaved;

    @Inject
    BoardManager boardManager;

    @Inject
    FilterEngine filterEngine;

    private FilterLayoutCallback callback;
    private Filter filter;

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        label = findViewById(R.id.filter_label);
        label.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.label = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        typeText = findViewById(R.id.type);
        boardsSelector = findViewById(R.id.boards);
        actionText = findViewById(R.id.action);
        pattern = findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.pattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        negativePattern = findViewById(R.id.negative_pattern);
        negativePattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.negativePattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = findViewById(R.id.pattern_preview_status);
        ImageView help = findViewById(R.id.help);
        colorPreview = findViewById(R.id.color_preview);
        applyToReplies = findViewById(R.id.apply_to_replies_checkbox);
        onlyOnOP = findViewById(R.id.only_on_op_checkbox);
        applyToSaved = findViewById(R.id.apply_to_saved_checkbox);

        colorPreview.setOnClickListener(this::onColorClicked);
        help.setOnClickListener(this::onHelpClicked);
        typeText.setOnClickListener(this::onTypesClicked);
        boardsSelector.setOnClickListener(this::onBoardsClicked);
        actionText.setOnClickListener(this::onActionsClicked);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;

        label.setText(filter.label);
        pattern.setText(filter.pattern);
        negativePattern.setText(filter.negativePattern);

        updateFilterValidity();
        updateFilterType();
        updateFilterAction();
        updateCheckboxes();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.applyToReplies = applyToReplies.isChecked();
        filter.onlyOnOP = onlyOnOP.isChecked();
        filter.applyToSaved = applyToSaved.isChecked();

        return filter;
    }

    private void onTypesClicked(View v) {
        @SuppressWarnings("unchecked")
        final SelectLayout<FilterType> selectLayout =
                (SelectLayout<FilterType>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

        List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
        for (FilterType filterType : FilterType.values()) {
            String name = filterType.toString();
            boolean checked = filter.hasFilter(filterType);

            items.add(new SelectLayout.SelectItem<>(filterType, filterType.flag, name, null, name, checked));
        }

        selectLayout.setItems(items);

        getDefaultAlertBuilder(getContext()).setView(selectLayout).setPositiveButton(R.string.ok, (dialog, which) -> {
            List<SelectLayout.SelectItem<FilterType>> items12 = selectLayout.getItems();
            int flags = 0;
            for (SelectLayout.SelectItem<FilterType> item : items12) {
                if (item.checked) {
                    flags |= item.item.flag;
                }
            }

            filter.type = flags;
            updateFilterType();
            updatePatternPreview();
        }).show();
    }

    private void onBoardsClicked(View v) {
        @SuppressWarnings("unchecked")
        final SelectLayout<Board> selectLayout =
                (SelectLayout<Board>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

        List<SelectLayout.SelectItem<Board>> items = new ArrayList<>();

        Boards allSavedBoards = new Boards();
        for (BoardRepository.SiteBoards item : boardManager.getSavedBoardsObservable().get()) {
            allSavedBoards.addAll(item.boards);
        }

        for (Board board : allSavedBoards) {
            String name = board.getFormattedName();
            boolean checked = filterEngine.matchesBoard(filter, board);

            items.add(new SelectLayout.SelectItem<>(board, board.id, name, "", name, checked));
        }

        selectLayout.setItems(items);

        getDefaultAlertBuilder(getContext()).setView(selectLayout).setPositiveButton(R.string.ok, (dialog, which) -> {
            List<SelectLayout.SelectItem<Board>> items1 = selectLayout.getItems();
            boolean all = selectLayout.areAllChecked();
            Boards boardList = new Boards(items1.size());
            if (!all) {
                for (SelectLayout.SelectItem<Board> item : items1) {
                    if (item.checked) {
                        boardList.add(item.item);
                    }
                }
                if (boardList.isEmpty()) {
                    all = true;
                }
            }

            filterEngine.saveBoardsToFilter(boardList, all, filter);

            updateBoardsSummary();
        }).show();
    }

    private void onActionsClicked(View v) {
        List<FloatingMenuItem<FilterAction>> menuItems = new ArrayList<>(6);

        for (FilterAction action : FilterAction.values()) {
            menuItems.add(new FloatingMenuItem<>(action, FilterAction.actionName(action)));
        }

        FloatingMenu<FilterAction> menu = new FloatingMenu<>(v.getContext(), v, menuItems);
        menu.setAnchorGravity(Gravity.LEFT, (int) -dp(getContext(), 5), (int) -dp(getContext(), 5));
        menu.setCallback(new FloatingMenu.ClickCallback<FilterAction>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<FilterAction> menu, FloatingMenuItem<FilterAction> item
            ) {
                filter.action = item.getId().ordinal();
                updateFilterAction();
            }
        });
        menu.show();
    }

    private void onHelpClicked(View v) {
        Map<String, StyleAction> extraStyles = new HashMap<>();
        extraStyles.put("tt", (node, text) -> span(text, new BackgroundColorSpanHashed(0x22000000)));
        extraStyles.put("i", (node, text) -> span(text, new BackgroundColorSpanHashed(0x22000000)));
        SpannableStringBuilder message = new SpannableStringBuilder(StyledHtml.fromHtml(getString(R.string.filter_help),
                null,
                extraStyles,
                Collections.emptyMap(),
                Collections.emptyList()
        ));

        getDefaultAlertBuilder(getContext())
                .setTitle(R.string.filter_help_title)
                .setMessage(message)
                .setNegativeButton("Open Regex101", (dialog1, which) -> openLink("https://regex101.com/"))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void onColorClicked(View v) {
        View colorPickerView = LayoutInflater.from(getContext()).inflate(R.layout.layout_color_pick, null);
        ColorPickerView colorView = colorPickerView.findViewById(R.id.color_picker);
        SeekBar alphaBar = colorPickerView.findViewById(R.id.alpha_picker);
        TextView percent = colorPickerView.findViewById(R.id.progress);
        alphaBar.setOnSeekBarChangeListener((DefaultOnSeekBarChangeListener) (seekBar, progress, fromUser) -> {
            colorView.setColor(Color.argb(progress,
                    Color.red(colorView.getColor()),
                    Color.green(colorView.getColor()),
                    Color.blue(colorView.getColor())
            ));
            percent.setText(getQuantityString(R.plurals.percent,
                    (int) (progress * 100 / (float) seekBar.getMax())
            ));
        });
        colorView.setColor(filter.color);
        alphaBar.setProgress(Color.alpha(filter.color));
        percent.setText(getQuantityString(R.plurals.percent,
                (int) (alphaBar.getProgress() * 100 / (float) alphaBar.getMax())
        ));

        getDefaultAlertBuilder(getContext())
                .setView(colorPickerView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog1, which) -> {
                    filter.color = colorView.getColor();
                    updateFilterAction();
                })
                .show();
    }

    private void updateFilterValidity() {
        int extraFlags = (filter.type & FilterType.FLAG_CODE.flag) != 0 ? Pattern.CASE_INSENSITIVE : 0;
        boolean valid = !TextUtils.isEmpty(filter.pattern) && filterEngine.compile(filter.pattern, extraFlags) != null;
        pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));
        boolean negValid = TextUtils.isEmpty(filter.negativePattern)
                || filterEngine.compile(filter.negativePattern, extraFlags) != null;
        negativePattern.setError(negValid ? null : getString(R.string.filter_invalid_pattern));

        if (callback != null) {
            callback.setSaveButtonEnabled(valid && negValid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_all);
        } else {
            text += filterEngine.getFilterBoardCount(filter);
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        applyToReplies.setChecked(filter.applyToReplies);
        onlyOnOP.setChecked(filter.onlyOnOP);
        applyToSaved.setChecked(filter.applyToSaved);
        if (filter.action == WATCH.ordinal()) {
            applyToReplies.setEnabled(false);
            onlyOnOP.setChecked(true);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
        }
    }

    private void updateFilterAction() {
        FilterAction action = FilterAction.values()[filter.action];
        actionText.setText(FilterAction.actionName(action));
        colorPreview.setVisibility(action == COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
        if (filter.action != WATCH.ordinal()) {
            applyToReplies.setEnabled(true);
            onlyOnOP.setEnabled(true);
            onlyOnOP.setChecked(false);
            applyToSaved.setEnabled(true);
        } else {
            applyToReplies.setEnabled(false);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
            if (applyToReplies.isChecked()) {
                applyToReplies.toggle();
                filter.applyToReplies = false;
            }
            if (!onlyOnOP.isChecked()) {
                onlyOnOP.toggle();
                filter.onlyOnOP = true;
            }
            if (applyToSaved.isChecked()) {
                applyToSaved.toggle();
                filter.applyToSaved = false;
            }
        }
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(filter.type).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = filterEngine.matches(filter, FilterType.forFlags(filter.type).get(0), text, true);
        patternPreviewStatus.setImageResource(matches
                ? R.drawable.ic_fluent_checkmark_24_filled
                : R.drawable.ic_fluent_dismiss_24_filled);
        patternPreviewStatus.setImageTintList(ColorStateList.valueOf(matches
                ? getColor(R.color.md_green_800)
                : getColor(R.color.md_red_800)));
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
