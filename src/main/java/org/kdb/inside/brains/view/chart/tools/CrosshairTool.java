package org.kdb.inside.brains.view.chart.tools;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCrosshairLabelGenerator;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.kdb.inside.brains.view.chart.BaseChartPanel;
import org.kdb.inside.brains.view.chart.ChartOptions;
import org.kdb.inside.brains.view.chart.ChartTool;

import java.awt.*;
import java.awt.geom.Point2D;
import java.text.NumberFormat;

public class CrosshairTool extends CrosshairOverlay implements ChartTool, ChartMouseListener {
    private Crosshair range;
    private Crosshair domain;

    private final BaseChartPanel myPanel;
    private final ChartOptions myOptions;

    public static final JBColor CROSSHAIR_PAINT = new JBColor(new Color(0xa4a4a5), new Color(0xa4a4a5));
    public static final JBColor CROSSHAIR_LABEL = new JBColor(new Color(0x595959), new Color(0x595959));
    public static final JBColor CROSSHAIR_OUTLINE = new JBColor(new Color(0xe0e0e0), new Color(0xe0e0e0));
    public static final JBColor CROSSHAIR_BACKGROUND = new JBColor(new Color(0xc4c4c4), new Color(0xc4c4c4));

    public CrosshairTool(BaseChartPanel panel, ChartOptions options) {
        myPanel = panel;
        myPanel.addOverlay(this);
        myPanel.addChartMouseListener(this);

        myOptions = options;
    }

    @Override
    public void setChart(JFreeChart chart) {
        clearRangeCrosshairs();
        clearDomainCrosshairs();

        if (chart != null) {
            range = createCrosshair(true);
            addRangeCrosshair(range);

            domain = createCrosshair(false);
            addDomainCrosshair(this.domain);
        }

        fireOverlayChanged();
    }

    @Override
    public void chartMouseClicked(ChartMouseEvent event) {
    }

    @Override
    public void chartMouseMoved(ChartMouseEvent event) {
        final Point2D p = myPanel.calculateValuesPoint(event);
        range.setValue(p.getY());
        domain.setValue(p.getX());
    }

    @NotNull
    private Crosshair createCrosshair(boolean vertical) {
        final Crosshair crosshair = new Crosshair(Double.NaN, CROSSHAIR_PAINT, new BasicStroke(0.5F));
        crosshair.setValue(Double.NaN);
        crosshair.setLabelVisible(true);
        crosshair.setLabelAnchor(vertical ? RectangleAnchor.LEFT : RectangleAnchor.BOTTOM);
        crosshair.setLabelPaint(CROSSHAIR_LABEL);
        crosshair.setLabelOutlinePaint(CROSSHAIR_OUTLINE);
        crosshair.setLabelBackgroundPaint(CROSSHAIR_BACKGROUND);
        final ValueAxis domainAxis = ((XYPlot) myPanel.getChart().getPlot()).getDomainAxis();
        if (!vertical && domainAxis instanceof DateAxis) {
            final DateAxis dateAxis = (DateAxis) domainAxis;
            crosshair.setLabelGenerator(g -> dateAxis.getTickUnit().valueToString(g.getValue()));
        } else {
            crosshair.setLabelGenerator(new StandardCrosshairLabelGenerator("  {0}  ", NumberFormat.getNumberInstance()));
        }

        return crosshair;
    }

    @Override
    public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {
        if (isEnabled()) {
            super.paintOverlay(g2, chartPanel);
        }
    }

    public boolean isEnabled() {
        return myOptions.isCrosshairToolEnabled();
    }

    public void setEnabled(boolean enabled) {
        myOptions.setCrosshairToolEnabled(enabled);
        fireOverlayChanged();
    }
}
