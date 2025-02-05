package org.kdb.inside.brains.view.console.table;

import com.intellij.find.FindModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.KdbIcons;
import kx.c;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kdb.inside.brains.UIUtils;
import org.kdb.inside.brains.core.KdbQuery;
import org.kdb.inside.brains.core.KdbResult;
import org.kdb.inside.brains.settings.KdbSettingsService;
import org.kdb.inside.brains.view.KdbOutputFormatter;
import org.kdb.inside.brains.view.chart.ChartActionGroup;
import org.kdb.inside.brains.view.console.ConsoleOptions;
import org.kdb.inside.brains.view.export.ClipboardExportAction;
import org.kdb.inside.brains.view.export.ExportDataProvider;
import org.kdb.inside.brains.view.export.ExportingType;
import org.kdb.inside.brains.view.export.OpenInEditorAction;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.max;

public class TableResultView extends NonOpaquePanel implements DataProvider, ExportDataProvider {
    private TableResult tableResult;
    private ToggleAction searchAction;

    private final Project project;
    private final ConsoleOptions consoleOptions;
    private final BiConsumer<KdbQuery, TableResultView> repeater;

    private final JBTable myTable;
    private final Splitter splitter;
    private final JScrollPane tableScroll;
    private final TableResultSearchSession searchSession;

    private ColumnsFilterPanel columnsFilter;
    private TableResultStatusPanel statusBar;

    public static final DataKey<TableResultView> DATA_KEY = DataKey.create("KdbConsole.TableResultView");

    public TableResultView(Project project, KdbOutputFormatter formatter) {
        this(project, formatter, TableMode.NORMAL);
    }

    public TableResultView(Project project, KdbOutputFormatter formatter, TableMode mode) {
        this(project, formatter, mode, null);
    }

    public TableResultView(Project project, KdbOutputFormatter formatter, TableMode mode, BiConsumer<KdbQuery, TableResultView> repeater) {
        this.project = project;
        this.repeater = repeater;

        consoleOptions = KdbSettingsService.getInstance().getConsoleOptions();
        final var valueColumnRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (consoleOptions.isStriped() && row % 2 == 0) {
                        c.setBackground(UIUtil.getDecoratedRowColor());
                    } else {
                        c.setBackground(table.getBackground());
                    }
                }
                return c;
            }

            @Override
            protected void setValue(Object value) {
                setText(formatter.objectToString(value));
            }
        };

        myTable = new JBTable(TableResult.EMPTY_MODEL) {
            @Override
            public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
                final Component c = super.prepareRenderer(renderer, row, column);

                final TableColumn tableColumn = getColumnModel().getColumn(column);
                tableColumn.setPreferredWidth(max(c.getPreferredSize().width + getIntercellSpacing().width + 20, tableColumn.getPreferredWidth()));

                final boolean keyColumn = getModel().isKeyColumn(tableColumn.getModelIndex());
                if (keyColumn) {
                    c.setBackground(UIUtils.getKeyColumnColor(c.getBackground()));
                }
                return c;
            }

            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                return valueColumnRenderer;
            }

            @Override
            public TableResult.QTableModel getModel() {
                return (TableResult.QTableModel) super.getModel();
            }

            @Override
            protected TableColumnModel createDefaultColumnModel() {
                return new MyTableColumnModel();
            }

            @Override
            public void setModel(@NotNull TableModel model) {
                super.setModel(model);

                if (model instanceof TableResult.QTableModel) {
                    final TableResult.QTableModel qModel = (TableResult.QTableModel) model;

                    final TableRowSorter<TableResult.QTableModel> sorter = new TableRowSorter<>(qModel);
                    sorter.setStringConverter(new TableStringConverter() {
                        @Override
                        public String toString(TableModel model, int row, int column) {
                            final Object valueAt = model.getValueAt(row, column);
                            if (valueAt == null) {
                                return "";
                            }
                            return formatter.objectToString(valueAt);
                        }
                    });

                    final TableResult.QColumnInfo[] columns = qModel.getColumns();
                    for (int i = 0; i < columns.length; i++) {
                        final Comparator<Object> comparator = columns[i].getComparator();
                        if (comparator != null) {
                            sorter.setComparator(i, comparator);
                        }
                    }
                    setRowSorter(sorter);
                }
            }
        };

        myTable.setStriped(false);
        myTable.setShowGrid(consoleOptions.isShowGrid());

        myTable.setShowColumns(true);
        myTable.setColumnSelectionAllowed(true);

        myTable.setAutoCreateRowSorter(false);
        myTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Disable original handler
        myTable.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return NONE;
            }
        });

        myTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                magicMouseActions(e);
            }
        });

        initializeColumnHighlither();

        searchSession = new TableResultSearchSession(myTable, project, new FindModel());
        searchSession.getFindModel().addObserver(this::modelBeenUpdated);

        createSearchComponent();

        tableScroll = ScrollPaneFactory.createScrollPane(myTable, true);
        if (consoleOptions.isIndexColumn()) {
            tableScroll.setRowHeaderView(new RowNumberView(myTable));
        }

        setLayout(new BorderLayout());

        splitter = new Splitter(false, 1);
        splitter.setFirstComponent(tableScroll);
        splitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE);
        splitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
        splitter.setHonorComponentsMinimumSize(true);
        splitter.setAllowSwitchOrientationByMouseClick(false);
        splitter.setShowDividerIcon(false);
        splitter.setShowDividerControls(false);

        add(splitter, BorderLayout.CENTER);

        add(searchSession.getComponent(), BorderLayout.NORTH);

        if (mode.showStatusBar()) {
            statusBar = new TableResultStatusPanel(myTable, formatter);
            add(statusBar, BorderLayout.SOUTH);
        }

        add(createFilter(), BorderLayout.EAST);
        add(createActions(), BorderLayout.WEST);
    }

    @NotNull
    private JComponent createFilter() {
        final DefaultActionGroup group = new DefaultActionGroup();

        final ToggleAction action = new DumbAwareToggleAction("Filter Columns", "Filter columns list", KdbIcons.Console.ColumnsFilter) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return splitter.getSecondComponent() != null;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                if (state) {
                    columnsFilter = new ColumnsFilterPanel(myTable);
                    splitter.setSecondComponent(columnsFilter);
                    columnsFilter.requestFocus();
                } else {
                    splitter.setSecondComponent(null);
                    columnsFilter.destroy();
                    columnsFilter = null;
                }
            }
        };
        group.add(action);

        final ActionToolbar filterToolbar = ActionManager.getInstance().createActionToolbar("TableResultView.Filter", group, false);
        filterToolbar.setTargetComponent(this);
        return filterToolbar.getComponent();
    }

    @NotNull
    private JComponent createActions() {
        final DefaultActionGroup group = new DefaultActionGroup();

        if (repeater != null) {
            group.add(new DumbAwareAction("_Repeat the Query", "Re-run the query related with this result", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    repeater.accept(tableResult.getQuery(), TableResultView.this);
                }
            });
            group.addSeparator();
        }

        group.add(searchAction);
        group.addSeparator();

        final ToggleAction action = new DumbAwareToggleAction("Show Index Column", "Show column with row indexes", KdbIcons.Console.TableIndex) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return isShowIndexColumn();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                setShowIndexColumn(state);
            }
        };
        action.registerCustomShortcutSet(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, myTable);
        group.add(action);

        group.addSeparator();
        group.addAll(ExportDataProvider.createActionGroup(project, this));

        group.addSeparator();
        group.add(new ChartActionGroup(myTable));

        PopupHandler.installPopupHandler(myTable, group, "TableResultView.Context");

        final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("TableResultView.Actions", group, false);
        actionToolbar.setTargetComponent(this);

        return actionToolbar.getComponent();
    }

    private void initializeColumnHighlither() {
        final JTableHeader header = myTable.getTableHeader();

        final Border normalBorder = JBUI.Borders.emptyBottom(1);
        final Border selectedBorder = JBUI.Borders.customLine(myTable.getSelectionBackground(), 0, 0, 1, 0);

        final TableCellRenderer defaultRenderer = header.getDefaultRenderer();
        header.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            final Component cmp = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (cmp instanceof JComponent) {
                final JComponent component = (JComponent) cmp;
                final boolean selectedIndex = table.getColumnModel().getSelectionModel().isSelectedIndex(column);
                final Border border = selectedIndex ? selectedBorder : normalBorder;
                final Border merge = JBUI.Borders.merge(component.getBorder(), border, true);
                component.setBorder(merge);
            }
            return cmp;
        });
        myTable.getColumnModel().getSelectionModel().addListSelectionListener(e -> header.repaint());
    }

    public boolean isShowIndexColumn() {
        return getNumberTable() != null;
    }

    public void setShowIndexColumn(boolean show) {
        final RowNumberView numberTable = getNumberTable();
        if (show) {
            if (numberTable == null) {
                tableScroll.setRowHeaderView(new RowNumberView(myTable));
            }
        } else {
            if (numberTable != null) {
                numberTable.dispose();
                tableScroll.setRowHeaderView(null);
            }
        }
    }

    private RowNumberView getNumberTable() {
        final JViewport rowHeader = tableScroll.getRowHeader();
        if (rowHeader == null) {
            return null;
        }
        return (RowNumberView) rowHeader.getView();
    }

    private void magicMouseActions(MouseEvent e) {
        if (e.getClickCount() != 2 || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        if (e.isAltDown()) {
            new OpenInEditorAction(null, this).performExport(project, this);
        } else {
            if (expandSelectedCell()) {
                return;
            }
            new ClipboardExportAction(null, ExportingType.SELECTION, this).performExport(project, this);
        }
    }

    private boolean expandSelectedCell() {
        final int c = myTable.getSelectedColumn();
        final int r = myTable.getSelectedRow();
        if (c >= 0 && r >= 0) {
            final Object v = myTable.getValueAt(r, c);
            if (isExpandable(v)) {
                final TabsTableResult owner = TabsTableResult.findParentTabs(this);
                if (owner != null) {
                    final TableResult from = TableResult.from(new KdbQuery("Expanded result at row=" + r + ", col=" + c), KdbResult.with(v));
                    owner.showTabAfter("Expanded Result", from);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isExpandable(Object o) {
        if (o == null) {
            return false;
        }
        if (TableResult.isNotEmptyList(o) && consoleOptions.isExpandList()) {
            return true;
        }
        if (TableResult.isTable(o) && consoleOptions.isExpandTable()) {
            return true;
        }
        return o instanceof c.Dict && consoleOptions.isExpandDict();
    }

    public JComponent getFocusableComponent() {
        return myTable;
    }

    static TableRowFilter createFilter(FindModel model) {
        final String text = model.getStringToFind();
        if (text.isBlank()) {
            return null;
        }
        return createSimpleFilter(model);
    }

    @Override
    public JBTable getTable() {
        return myTable;
    }

    private void createSearchComponent() {
        searchAction = new DumbAwareToggleAction("_Search", "Search data in the table", AllIcons.Actions.Search) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return searchSession.isOpened();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                if (state) {
                    searchSession.open();
                } else {
                    searchSession.close();
                }
            }
        };
        searchAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK)), myTable);
    }

    @NotNull
    static TableRowFilter createSimpleFilter(FindModel model) {
        final String text = model.getStringToFind();
        if (model.isRegularExpressions()) {
            final Matcher matcher = model.compileRegExp().matcher("");
            return new TableRowFilter(v -> matcher.reset(v).find());
        } else if (model.isWholeWordsOnly()) {
            final int flags = model.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
            final Pattern compile = Pattern.compile(".*\\b" + text + "\\b.*", flags);
            final Matcher matcher = compile.matcher("");
            return new TableRowFilter(v -> matcher.reset(v).find());
        } else {
            return new TableRowFilter(v -> containsIgnoreCase(v, text, !model.isCaseSensitive()));
        }
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
        if (DATA_KEY.is(dataId) || ExportDataProvider.DATA_KEY.is(dataId)) {
            return this;
        }
        return null;
    }

    @Override
    public String getExportName() {
        final Container parent = getParent();
        if (parent instanceof JBTabs) {
            final JBTabs jbTabs = (JBTabs) parent;
            for (TabInfo info : jbTabs.getTabs()) {
                if (info.getObject() == this) {
                    return info.getText();
                }
            }
        }
        return "Table Result";
    }

    @Override
    public Object getNativeObject() {
        return tableResult.getResult().getObject();
    }

    private static boolean containsIgnoreCase(String str, String searchStr, boolean ignoreCase) {
        if (str == null || searchStr == null) {
            return false;
        }

        final int length = searchStr.length();
        if (length == 0) {
            return true;
        }

        for (int i = str.length() - length; i >= 0; i--) {
            if (str.regionMatches(ignoreCase, i, searchStr, 0, length)) {
                return true;
            }
        }
        return false;
    }

    public void showResult(TableResult tableResult) {
        this.tableResult = tableResult;

        if (tableResult == null) {
            myTable.setModel(TableResult.EMPTY_MODEL);
            searchSession.setDelaySearchEnabled(false);
        } else {
            final TableResult.QTableModel tableModel = tableResult.getTableModel();
            myTable.setModel(tableModel);

            // 10_000 rows by 20 columns can be sorted fast. No reason for delay
            searchSession.setDelaySearchEnabled(tableModel.getRowCount() * tableModel.getColumnCount() > 200_000);

            updateHeaderWidth();
            modelBeenUpdated(searchSession.getFindModel());
        }

        if (columnsFilter != null) {
            columnsFilter.invalidateFilter();
        }

        if (statusBar != null) {
            statusBar.showResult(tableResult);
        }
    }

    private void updateHeaderWidth() {
        final TableCellRenderer renderer = myTable.getTableHeader().getDefaultRenderer();
        final TableColumnModel columnModel = myTable.getColumnModel();
        for (int i = 0; i < myTable.getColumnCount(); ++i) {
            columnModel.getColumn(i).setPreferredWidth(renderer.getTableCellRendererComponent(myTable, myTable.getModel().getColumnName(i), false, false, 0, i).getPreferredSize().width);
        }
    }

    @FunctionalInterface
    private interface ValueFilter {
        boolean check(String value);
    }

    private void modelBeenUpdated(FindModel findModel) {
        final RowFilter<TableModel, Integer> filter = createFilter(findModel);
        ((TableRowSorter<? extends TableModel>) myTable.getRowSorter()).setRowFilter(filter);

        final RowNumberView numberTable = getNumberTable();
        if (numberTable != null) {
            numberTable.invalidate();
        }

        if (statusBar != null) {
            statusBar.invalidateRowsCount();
        }
    }

    static class TableRowFilter extends RowFilter<TableModel, Integer> {
        private final ValueFilter filter;

        public TableRowFilter(ValueFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean include(Entry<? extends TableModel, ? extends Integer> value) {
            int index = value.getValueCount();
            while (--index >= 0) {
                final String stringValue = value.getStringValue(index);
                if (filter.check(stringValue)) {
                    return true;
                }
            }
            return false;
        }
    }
}