package org.kdb.inside.brains.view.chart;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tabs.*;
import com.intellij.util.ui.JBUI;
import icons.KdbIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.kdb.inside.brains.action.ActionPlaces;
import org.kdb.inside.brains.settings.KdbSettingsService;
import org.kdb.inside.brains.view.PopupActionGroup;
import org.kdb.inside.brains.view.chart.template.ChartTemplate;
import org.kdb.inside.brains.view.chart.template.ChartTemplatesService;
import org.kdb.inside.brains.view.chart.template.CreateTemplateDialog;
import org.kdb.inside.brains.view.chart.template.TemplatesEditorDialog;
import org.kdb.inside.brains.view.chart.tools.CrosshairTool;
import org.kdb.inside.brains.view.chart.tools.MeasureTool;
import org.kdb.inside.brains.view.chart.tools.ToolToggleAction;
import org.kdb.inside.brains.view.chart.tools.ValuesTool;
import org.kdb.inside.brains.view.chart.types.ChartType;
import org.kdb.inside.brains.view.chart.types.line.LineChartProvider;
import org.kdb.inside.brains.view.chart.types.ohlc.OHLCChartViewProvider;
import org.kdb.inside.brains.view.export.ExportDataProvider;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ui.JBUI.Borders;

public class ChartingDialog extends FrameWrapper implements DataProvider {
    private static final String EMPTY_CARD_PANEL_NAME = "EMPTY";

    private final ValuesTool valuesTool;
    private final MeasureTool measureTool;
    private final CrosshairTool crosshairTool;
    private static final String CHART_CARD_PANEL_NAME = "CHART";
    private final JBTabs chartTabs;
    private final BaseChartPanel chartPanel;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel chartLayoutPanel = new JPanel(cardLayout);

    private final Splitter splitter = new Splitter(true, 0.75f);
    private final JButton templateButton = new JButton("Create Template");
    private final ComboBox<ChartTemplate> templatesComboBox = new ComboBox<>();

    protected ChartingDialog(@NotNull Project project, String title, ChartDataProvider dataProvider, ChartTemplate template) {
        super(project, "KdbInsideBrains-Charting", false, title);

        final ChartOptions chartOptions = KdbSettingsService.getInstance().getChartOptions();

        chartPanel = new BaseChartPanel(chartOptions, this::createPopupMenu);

        valuesTool = new ValuesTool(project, chartPanel, chartOptions);
        measureTool = new MeasureTool(chartPanel, chartOptions);
        crosshairTool = new CrosshairTool(chartPanel, chartOptions);

        chartTabs = createTabs(project, dataProvider);

        templateButton.setEnabled(false);
        templateButton.addActionListener(e -> upsertTemplate(project, dataProvider));

        final JButton close = new JButton("Close");
        close.addActionListener(e -> close());

        final JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(Borders.customLine(JBColor.LIGHT_GRAY, 1, 0, 0, 0));
        buttonPanel.add(templateButton, BorderLayout.WEST);
        buttonPanel.add(close, BorderLayout.EAST);

        final JComponent templatePanel = createTemplatePanel(project, dataProvider);

        final JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(templatePanel, BorderLayout.NORTH);
        topPanel.add(chartTabs.getComponent(), BorderLayout.CENTER);

        final JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.add(topPanel, BorderLayout.EAST);
        configPanel.add(buttonPanel, BorderLayout.SOUTH);
        configPanel.setBorder(Borders.customLine(JBColor.LIGHT_GRAY, 0, 1, 0, 0));

        final JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(configPanel, BorderLayout.EAST);
        eastPanel.add(createToolbar(), BorderLayout.WEST);

        chartLayoutPanel.add(chartPanel, CHART_CARD_PANEL_NAME);
        chartLayoutPanel.add(createEmptyPanel(), EMPTY_CARD_PANEL_NAME);

        splitter.setFirstComponent(chartLayoutPanel);
        if (valuesTool.isEnabled()) {
            splitter.setSecondComponent(valuesTool.getComponent());
        }

        final JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(eastPanel, BorderLayout.EAST);
        rootPanel.add(splitter, BorderLayout.CENTER);
        rootPanel.setBorder(new CompoundBorder(Borders.empty(0, 10, 10, 10), Borders.customLine(JBColor.LIGHT_GRAY)));

        setComponent(rootPanel);
        setImage(IconLoader.toImage(KdbIcons.Chart.Icon));
        closeOnEsc();
        configChanged();
        updateChartByTemplate(template);
    }

    private void upsertTemplate(@NotNull Project project, ChartDataProvider dataProvider) {
        final ChartViewProvider<JPanel, ChartConfig> provider = getSelectedProvider();
        if (provider == null) {
            return;
        }

        final ChartConfig config = provider.getChartConfig();
        ChartTemplate template = templatesComboBox.getItem();
        if (template != null) {
            template.setConfig(config);
            templateButton.setEnabled(false);
        } else {
            template = new ChartTemplate(config);
            final CreateTemplateDialog d = new CreateTemplateDialog(project, template);
            if (d.showAndGet()) {
                ChartTemplatesService.getService(project).insertTemplate(template);
                invalidateTemplatesList(project, dataProvider);
                templateButton.setEnabled(false);
                templatesComboBox.setSelectedItem(template);
            }
        }
    }

    private void invalidateTemplatesList(@NotNull Project project, ChartDataProvider dataProvider) {
        final ChartTemplatesService service = ChartTemplatesService.getService(project);

        final List<ChartTemplate> templates = service.getTemplates().stream().filter(t -> t.getConfig().isApplicable(dataProvider)).collect(Collectors.toList());
        templates.add(0, null); // No template element. Selected by default

        final Object selectedItem = templatesComboBox.getSelectedItem();
        templatesComboBox.setModel(new DefaultComboBoxModel<>(templates.toArray(ChartTemplate[]::new)));
        templatesComboBox.setSelectedItem(selectedItem);
    }

    private void updateChartByTemplate(ChartTemplate template) {
        if (templatesComboBox.getItem() != template) {
            templatesComboBox.setSelectedItem(template);
        }
        templateButton.setText(template == null ? "Create Template" : "Update Template");
        if (template == null) {
            templateButton.setEnabled(!getSelectedProvider().getChartConfig().isInvalid());
            return;
        }

        final ChartConfig config = template.getConfig();

        final ChartType type = config.getType();
        final TabInfo tab = findTabInfo(type);
        if (tab == null) {
            return;
        }
        chartTabs.select(tab, false);
        getProvider(tab).setChartConfig(config.copy());
    }

    private JPanel createTemplatePanel(@NotNull Project project, @NotNull ChartDataProvider dataProvider) {
        invalidateTemplatesList(project, dataProvider);
        templatesComboBox.setEditable(false);
        templatesComboBox.addItemListener(e -> updateChartByTemplate(templatesComboBox.getItem()));

        templatesComboBox.setRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends ChartTemplate> list, ChartTemplate value, int index, boolean selected, boolean hasFocus) {
                if (value == null) {
                    setIcon(null);
                    append("No template", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    setIcon(value.getIcon());
                    append(value.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
                }
            }
        });

        final ActionButton manage = new ActionButton(new AnAction("Modify Templates", "Manage charting templates", KdbIcons.Chart.Templates) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                TemplatesEditorDialog.showDialog(project, templatesComboBox.getItem());
                invalidateTemplatesList(project, dataProvider);
            }
        }, null, ActionPlaces.CHARTS_PANEL_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);

        final JPanel p1 = new JPanel(new BorderLayout());
        p1.add(new JLabel("Templates:"), BorderLayout.WEST);
        p1.add(manage, BorderLayout.EAST);

        final JPanel p = new JPanel(new BorderLayout());
        p.add(p1, BorderLayout.NORTH);
        p.add(templatesComboBox, BorderLayout.CENTER);

        p.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBColor.LIGHT_GRAY, 0, 0, 1, 0), JBUI.Borders.empty(5, 3)));
        return p;
    }

    private ActionGroup createPopupMenu() {
        final DefaultActionGroup g = new DefaultActionGroup();
        final List<ChartTool> crosshairTool = List.of(this.crosshairTool, measureTool, valuesTool);
        for (ChartTool tool : crosshairTool) {
            if (!tool.isEnabled()) {
                continue;
            }

            final ActionGroup popupActions = tool.getPopupActions();
            if (popupActions.getChildren(null).length != 0) {
                final String templateText = popupActions.getTemplateText();
                if (templateText != null) {
                    g.addSeparator(templateText);
                }
                g.addAll(popupActions);
            }
        }
        return g;
    }

    private JBTabs createTabs(Project project, ChartDataProvider dataProvider) {
        final JBTabs tabs = JBTabsFactory.createTabs(project, this);

        final JBTabsPresentation presentation = tabs.getPresentation();
        presentation.setSingleRow(true);
        presentation.setSupportsCompression(true);
        presentation.setTabsPosition(JBTabsPosition.top);

        final List<ChartViewProvider<?, ?>> builders = List.of(new LineChartProvider(dataProvider), new OHLCChartViewProvider(dataProvider));

        final Insets borderInsets = UIManager.getBorder("Button.border").getBorderInsets(new JButton());
        for (ChartViewProvider<?, ?> builder : builders) {
            builder.addConfigListener(this::configChanged);

            final JComponent panel = builder.getConfigPanel();
            panel.setBorder(Borders.empty(0, borderInsets.right));

            final TabInfo info = new TabInfo(ScrollPaneFactory.createScrollPane(panel));
            info.setIcon(builder.getIcon());
            info.setText(builder.getName());
            info.setObject(builder);

            tabs.addTab(info);
        }

        tabs.addListener(new TabsListener() {
            @Override
            public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
                configChanged();
            }
        });
        return tabs;
    }

    private void configChanged() {
        final ChartViewProvider<JPanel, ChartConfig> provider = getSelectedProvider();
        if (provider == null) {
            templateButton.setEnabled(false);
            cardLayout.show(chartLayoutPanel, EMPTY_CARD_PANEL_NAME);
            return;
        }

        final ChartConfig config = provider.getChartConfig();
        final JFreeChart chart = config.isInvalid() ? null : provider.getJFreeChart(config);
        chartPanel.setChart(chart);

        final ChartTemplate template = templatesComboBox.getItem();

        templateButton.setEnabled(chart != null && (template == null || !config.equals(template.getConfig())));
        List.of(valuesTool, measureTool, crosshairTool).forEach(s -> s.setChart(chart));

        cardLayout.show(chartLayoutPanel, chart == null ? EMPTY_CARD_PANEL_NAME : CHART_CARD_PANEL_NAME);
    }

    private ChartViewProvider<JPanel, ChartConfig> getSelectedProvider() {
        return getProvider(chartTabs.getSelectedInfo());
    }

    private TabInfo findTabInfo(ChartType type) {
        final List<TabInfo> tabs = chartTabs.getTabs();
        for (TabInfo tab : tabs) {
            @SuppressWarnings("unchecked")
            ChartViewProvider<JPanel, ChartConfig> provider = (ChartViewProvider<JPanel, ChartConfig>) tab.getObject();
            if (provider.getType() == type) {
                return tab;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ChartViewProvider<JPanel, ChartConfig> getProvider(TabInfo info) {
        if (info == null) {
            return null;
        }
        return (ChartViewProvider<JPanel, ChartConfig>) info.getObject();
    }

    private static JPanel createEmptyPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JBColor.WHITE);
        panel.add(new JLabel("<html><center><h1>There is no data to show.</h1><br><br>Please select the chart type and appropriate configuration.<center></html>"));
        return panel;
    }

    private void measureSelected(boolean state) {
        if (measureTool.isEnabled() == state) {
            return;
        }

        measureTool.setEnabled(state);
        if (state) {
            valuesSelected(false);
        }
        chartPanel.setDefaultCursor(state);
    }

    private void valuesSelected(boolean state) {
        if (valuesTool.isEnabled() == state) {
            return;
        }
        valuesTool.setEnabled(state);
        if (state) {
            splitter.setSecondComponent(valuesTool.getComponent());
            measureSelected(false);
        } else {
            splitter.setSecondComponent(null);
        }
        chartPanel.setDefaultCursor(state);
    }

    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();

        group.add(new ToolToggleAction("Crosshair", "Show crosshair lines", KdbIcons.Chart.ToolCrosshair, crosshairTool::isEnabled, crosshairTool::setEnabled));
        group.addSeparator();

        group.add(new ToolToggleAction("Measure", "Measuring tool", KdbIcons.Chart.ToolMeasure, measureTool::isEnabled, this::measureSelected));
        group.add(new ToolToggleAction("Points Collector", "Writes each click into a table", KdbIcons.Chart.ToolPoints, valuesTool::isEnabled, this::valuesSelected));
        group.addSeparator();

        final DefaultActionGroup snapping = new PopupActionGroup("Snapping", KdbIcons.Chart.ToolMagnet);
        snapping.add(new SpanAction("_Disable Snapping", SnapType.NO));
        snapping.add(new SpanAction("Snap to _Line", SnapType.LINE));
        snapping.add(new SpanAction("Snap to _Vertex", SnapType.VERTEX));

        group.add(snapping);

        group.addSeparator();
        group.addAll(createChartPanelMenu());

        final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHARTS_PANEL_TOOLBAR, group, false);
        actionToolbar.setTargetComponent(chartLayoutPanel);

        final JComponent actionComponent = actionToolbar.getComponent();
        actionComponent.setBorder(new CompoundBorder(Borders.customLine(JBColor.LIGHT_GRAY, 0, 1, 0, 0), Borders.empty(5, 3)));

        return actionComponent;
    }

    private DefaultActionGroup createChartPanelMenu() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ChartAction(ChartPanel.COPY_COMMAND, "_Copy", "Copy the chart", AllIcons.Actions.Copy));

        final PopupActionGroup saveAs = new PopupActionGroup("_Save As", AllIcons.Actions.MenuSaveall);
        saveAs.add(new ChartAction("SAVE_AS_PNG", "PNG...", "Save as PNG image"));
        saveAs.add(new ChartAction("SAVE_AS_SVG", "SVG...", "Save as SVG image"));
        group.add(saveAs);

        group.addSeparator();

        final DefaultActionGroup zoomIn = new PopupActionGroup("Zoom _In", AllIcons.Graph.ZoomIn);
        zoomIn.add(new ChartAction(ChartPanel.ZOOM_IN_BOTH_COMMAND, "_Both Axes", "Zoom in both axes"));
        zoomIn.addSeparator();
        zoomIn.add(new ChartAction(ChartPanel.ZOOM_IN_RANGE_COMMAND, "_Range Axis", "Zoom in only range axis"));
        zoomIn.add(new ChartAction(ChartPanel.ZOOM_IN_DOMAIN_COMMAND, "_Domain Axis", "Zoom in only domain axis"));
        group.add(zoomIn);

        final DefaultActionGroup zoomOut = new PopupActionGroup("Zoom _Out", AllIcons.Graph.ZoomOut);
        zoomOut.add(new ChartAction(ChartPanel.ZOOM_OUT_BOTH_COMMAND, "_Both Axes", "Zoom out both axes"));
        zoomOut.addSeparator();
        zoomOut.add(new ChartAction(ChartPanel.ZOOM_OUT_RANGE_COMMAND, "_Range Axis", "Zoom out only range axis"));
        zoomOut.add(new ChartAction(ChartPanel.ZOOM_OUT_DOMAIN_COMMAND, "_Domain Axis", "Zoom out only domain axis"));
        group.add(zoomOut);

        final DefaultActionGroup zoomReset = new PopupActionGroup("Zoom _Reset", AllIcons.Graph.ActualZoom);
        zoomReset.add(new ChartAction(ChartPanel.ZOOM_RESET_BOTH_COMMAND, "_Both Axes", "Reset the chart zoom"));
        zoomReset.addSeparator();
        zoomReset.add(new ChartAction(ChartPanel.ZOOM_RESET_RANGE_COMMAND, "_Range Axis", "Reset zoom for range axis only"));
        zoomReset.add(new ChartAction(ChartPanel.ZOOM_RESET_DOMAIN_COMMAND, "_Domain Axis", "Reset zoom for domain axis only"));
        group.add(zoomReset);
        return group;
    }

    private class SpanAction extends CheckboxAction {
        private final SnapType snapType;

        private SpanAction(String name, SnapType snapType) {
            super(name);
            this.snapType = snapType;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return chartPanel.getSnapType() == snapType;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
                chartPanel.setSnapType(snapType);
            } else {
                chartPanel.setSnapType(SnapType.NO);
            }
        }
    }

    private class ChartAction extends AnAction {
        private final String command;

        public ChartAction(String command, String text, String description) {
            this(command, text, description, null);
        }

        public ChartAction(String command, String text, String description, Icon icon) {
            super(text, description, icon);
            this.command = command;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(chartPanel.getChart() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            chartPanel.actionPerformed(new ActionEvent(this, -1, command));
        }
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
        if (ExportDataProvider.DATA_KEY.is(dataId)) {
            return valuesTool;
        }
        return super.getData(dataId);
    }
}
