package org.kdb.inside.brains.view.console;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.*;

public class ConsoleOptionsPanel extends JPanel {
    private final JBCheckBox enlistArrays = new JBCheckBox("Enlist one item lists");
    private final JBCheckBox wrapString = new JBCheckBox("Wrap string with double quotes");
    private final JBCheckBox prefixSymbols = new JBCheckBox("Prefix symbols with grave accent");
    private final JBCheckBox showGrid = new JBCheckBox("Show table grid");
    private final JBCheckBox striped = new JBCheckBox("Stripe table rows");
    private final JBCheckBox indexColumn = new JBCheckBox("Show index column");
    private final JBCheckBox listAsTable = new JBCheckBox("Show list as table");
    private final JBCheckBox dictAsTable = new JBCheckBox("Show dict as table");
    private final JBCheckBox expandList = new JBCheckBox("Vector");
    private final JBCheckBox expandDict = new JBCheckBox("Dictionary");
    private final JBCheckBox expandFlip = new JBCheckBox("Table");
    private final JBCheckBox consoleBackground = new JBCheckBox("Use an instance color for console background");
    private final JBCheckBox xmasKeyColumn = new JBCheckBox("Show XMas key column");
    private final JBIntSpinner floatPrecisionEditor = new JBIntSpinner(7, 0, ConsoleOptions.MAX_DECIMAL_PRECISION);
    private final ComboBox<ConsoleSplitType> splitTypes = new ComboBox<>(ConsoleSplitType.values());

    public ConsoleOptionsPanel() {
        super(new BorderLayout());

        enlistArrays.setToolTipText("If enabled - an one element list will be shown as 'enlist'; comma is used otherwise.");

        final var formBuilder = FormBuilder.createFormBuilder();
        formBuilder.addComponent(showGrid);
        formBuilder.addComponent(striped);
        formBuilder.addComponent(indexColumn);
        formBuilder.addComponent(enlistArrays);
        formBuilder.addComponent(wrapString);
        formBuilder.addComponent(prefixSymbols);
        formBuilder.addComponent(listAsTable);
        formBuilder.addComponent(dictAsTable);
        formBuilder.addComponent(xmasKeyColumn);
        formBuilder.addLabeledComponent("Float precision: ", floatPrecisionEditor);
        createSplitTypes(formBuilder);
        addExpandPanel(formBuilder);
        formBuilder.addComponent(consoleBackground);

        add(formBuilder.getPanel());
    }

    private void createSplitTypes(FormBuilder formBuilder) {
        splitTypes.setEditable(false);
        splitTypes.setSelectedItem(ConsoleSplitType.NO);
        splitTypes.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, ((ConsoleSplitType) value).getLabel(), index, isSelected, cellHasFocus);
            }
        });

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(new JBLabel("Default console and results tabs splitting: "));
        p.add(splitTypes);

        formBuilder.addComponent(p);
    }


    private void addExpandPanel(FormBuilder formBuilder) {
        final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.add(expandList);
        p.add(expandDict);
        p.add(expandFlip);

        formBuilder.addComponent(new JBLabel("Expand value by double click: "));
        formBuilder.setFormLeftIndent(20);
        formBuilder.addComponent(p);
        formBuilder.setFormLeftIndent(0);
    }

    public ConsoleOptions getOptions() {
        final ConsoleOptions consoleOptions = new ConsoleOptions();
        consoleOptions.setEnlistArrays(enlistArrays.isSelected());
        consoleOptions.setFloatPrecision(floatPrecisionEditor.getNumber());
        consoleOptions.setWrapStrings(wrapString.isSelected());
        consoleOptions.setPrefixSymbols(prefixSymbols.isSelected());
        consoleOptions.setStriped(striped.isSelected());
        consoleOptions.setShowGrid(showGrid.isSelected());
        consoleOptions.setListAsTable(listAsTable.isSelected());
        consoleOptions.setDictAsTable(dictAsTable.isSelected());
        consoleOptions.setSplitType(splitTypes.getItem());
        consoleOptions.setIndexColumn(indexColumn.isSelected());
        consoleOptions.setExpandList(expandList.isSelected());
        consoleOptions.setExpandDict(expandDict.isSelected());
        consoleOptions.setExpandTable(expandFlip.isSelected());
        consoleOptions.setConsoleBackground(consoleBackground.isSelected());
        consoleOptions.setXmasKeyColumn(xmasKeyColumn.isSelected());
        return consoleOptions;
    }

    public void setOptions(ConsoleOptions consoleOptions) {
        floatPrecisionEditor.setNumber(consoleOptions.getFloatPrecision());
        enlistArrays.setSelected(consoleOptions.isEnlistArrays());
        wrapString.setSelected(consoleOptions.isWrapStrings());
        prefixSymbols.setSelected(consoleOptions.isPrefixSymbols());
        striped.setSelected(consoleOptions.isStriped());
        showGrid.setSelected(consoleOptions.isShowGrid());
        listAsTable.setSelected(consoleOptions.isListAsTable());
        dictAsTable.setSelected(consoleOptions.isDictAsTable());
        splitTypes.setItem(consoleOptions.getSplitType());
        indexColumn.setSelected(consoleOptions.isIndexColumn());
        expandList.setSelected(consoleOptions.isExpandList());
        expandDict.setSelected(consoleOptions.isExpandDict());
        expandFlip.setSelected(consoleOptions.isExpandTable());
        consoleBackground.setSelected(consoleOptions.isConsoleBackground());
        xmasKeyColumn.setSelected(consoleOptions.isXmasKeyColumn());
    }
}
