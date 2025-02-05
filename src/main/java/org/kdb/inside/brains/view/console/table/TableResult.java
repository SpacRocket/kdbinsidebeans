package org.kdb.inside.brains.view.console.table;

import com.intellij.util.ui.ColumnInfo;
import kx.c;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kdb.inside.brains.KdbType;
import org.kdb.inside.brains.core.KdbQuery;
import org.kdb.inside.brains.core.KdbResult;
import org.kdb.inside.brains.settings.KdbSettingsService;
import org.kdb.inside.brains.view.console.ConsoleOptions;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;

public class TableResult {
    private final KdbQuery query;
    private final KdbResult result;
    private final QTableModel tableModel;

    private static final String KEY_COLUMN_PREFIX = "\u00A1 ";
    private static final String KEY_COLUMN_PREFIX_XMAS = "\uD83C\uDF84 ";

    public static final QTableModel EMPTY_MODEL = new EmptyTableModel();

    private TableResult(KdbQuery query, KdbResult result, QTableModel tableModel) {
        this.query = query;
        this.result = result;
        this.tableModel = tableModel;
    }

    public KdbQuery getQuery() {
        return query;
    }

    public KdbResult getResult() {
        return result;
    }

    public QTableModel getTableModel() {
        return tableModel;
    }


    public TableResult copy() {
        return new TableResult(query, result, tableModel);
    }

    public static TableResult from(KdbQuery query, KdbResult result) {
        final Object k = result.getObject();
        if (k == null) {
            return null;
        }

        QTableModel model = createModel(k);
        return model == null ? null : new TableResult(query, result, model);
    }

    @Nullable
    private static QTableModel createModel(Object k) {
        if (k instanceof c.Flip) {
            return new SimpleTableModel((c.Flip) k);
        }

        final ConsoleOptions options = KdbSettingsService.getInstance().getConsoleOptions();
        if (isNotEmptyList(k) && options.isListAsTable()) {
            return new ListTableModel(k);
        }

        if (k instanceof c.Dict) {
            final c.Dict dict = (c.Dict) k;
            final Object x = dict.x;
            final Object y = dict.y;

            final boolean xa = x.getClass().isArray();
            final boolean ya = y.getClass().isArray();
            if (xa && ya) {
                if (options.isDictAsTable()) {
                    return new DictTableModel(x, y);
                }
            } else {
                if ((x instanceof c.Flip || xa) && (y instanceof c.Flip || ya)) {
                    return new DictTableModel(x, y);
                }
            }
        }
        return null;
    }

    public static boolean isTable(Object o) {
        if (o instanceof c.Flip) {
            return true;
        }
        if (o instanceof c.Dict) {
            final c.Dict d = (c.Dict) o;
            return (d.x instanceof c.Flip || d.x.getClass().isArray()) && (d.y instanceof c.Flip || d.y.getClass().isArray());
        }
        return false;
    }

    public static boolean isNotEmptyList(Object o) {
        return o.getClass().isArray() && !(o instanceof char[]) && Array.getLength(o) != 0;
    }

    public static abstract class QTableModel implements TableModel {
        final QColumnInfo[] columns;

        static final QColumnInfo[] EMPTY_COLUMNS = new QColumnInfo[0];

        protected QTableModel(QColumnInfo[] columns) {
            this.columns = columns;
        }

        @Override
        public void addTableModelListener(TableModelListener l) {
        }

        @Override
        public void removeTableModelListener(TableModelListener l) {
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException("Read-only model");
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        public QColumnInfo[] getColumns() {
            return columns;
        }

        @Nls
        @Override
        public String getColumnName(int columnIndex) {
            return columns[columnIndex].getName();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columns[columnIndex].getColumnClass();
        }

        public boolean isKeyColumn(int columnIndex) {
            return columns[columnIndex].key;
        }
    }

    private static class EmptyTableModel extends QTableModel {
        private EmptyTableModel() {
            super(EMPTY_COLUMNS);
        }

        @Override
        public int getRowCount() {
            return 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return null;
        }
    }

    public static class ListTableModel extends QTableModel {
        private final Object array;
        private final int rowsCount;

        protected ListTableModel(Object array) {
            this(array, array.getClass().getComponentType());
        }

        private ListTableModel(Object array, Class<?> type) {
            super(new QColumnInfo[]{new QColumnInfo(KdbType.typeOf(type).getTypeName(), type, false)});
            this.array = array;
            this.rowsCount = Array.getLength(array);
        }

        @Override
        public int getRowCount() {
            return rowsCount;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return Array.get(array, rowIndex);
        }
    }

    public static class DictTableModel extends QTableModel {
        private final Object keys;
        private final Object values;

        private final int keysCount;
        private final int rowsCount;

        private DictTableModel(Object keys, Object values) {
            this(keys, cols(keys, true), values, cols(values, false));
        }

        private DictTableModel(Object keys, QColumnInfo[] keysInfo, Object values, QColumnInfo[] valuesInfo) {
            super(ArrayUtils.addAll(keysInfo, valuesInfo));
            this.keys = keys;
            this.values = values;

            keysCount = keysInfo.length;
            rowsCount = Array.getLength(keys instanceof c.Flip ? ((c.Flip) keys).y[0] : keys);
        }

        private static QColumnInfo[] cols(Object v, boolean key) {
            if (v instanceof c.Flip) {
                return QColumnInfo.of((c.Flip) v, key);
            }
            return new QColumnInfo[]{
                    new QColumnInfo(key ? "Key" : "Value", v.getClass().getComponentType(), key)
            };
        }

        @Override
        public int getRowCount() {
            return rowsCount;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            final Object row = getRow(columnIndex);
            return Array.get(row, rowIndex);
        }

        public Object getRow(int columnIndex) {
            final Object o = columnIndex < keysCount ? keys : values;
            if (o instanceof c.Flip) {
                final c.Flip flip = (c.Flip) o;
                final int index = columnIndex < keysCount ? columnIndex : columnIndex - keysCount;
                return flip.y[index];
            }
            return o;
        }
    }

    public static class SimpleTableModel extends QTableModel {
        final c.Flip flip;
        final int rowsCount;

        private SimpleTableModel(c.Flip flip) {
            super(QColumnInfo.of(flip, false));
            this.flip = flip;
            this.rowsCount = Array.getLength(flip.y[0]);
        }

        @Override
        public int getRowCount() {
            return rowsCount;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return Array.get(flip.y[columnIndex], rowIndex);
        }
    }

    @NotNull
    private static String createColumnName(String name, boolean key) {
        return getColumnPrefix(key) + name;
    }

    @NotNull
    private static String getColumnPrefix(boolean key) {
        if (key) {
            if (KdbSettingsService.getInstance().getConsoleOptions().isXmasKeyColumn()) {
                final LocalDate now = LocalDate.now();
                if (now.getMonth() == Month.DECEMBER && now.getDayOfMonth() >= 14) {
                    return KEY_COLUMN_PREFIX_XMAS;
                }
            }
            return KEY_COLUMN_PREFIX;
        }
        return "";
    }

    public static class QColumnInfo extends ColumnInfo<Object, Object> {
        private final boolean key;
        private final Class<?> columnClass;
        private final Comparator<Object> comparator;

        @SuppressWarnings("unchecked")
        public QColumnInfo(String name, Class<?> columnClass, boolean key) {
            super(createColumnName(name, key));
            this.key = key;
            this.columnClass = columnClass;

            if (columnClass != null && (Comparable.class.isAssignableFrom(columnClass) || columnClass.isPrimitive())) {
                comparator = (o1, o2) -> ((Comparable<Object>) o1).compareTo(o2);
            } else {
                comparator = null;
            }
        }

        @Override
        public Class<?> getColumnClass() {
            return columnClass;
        }

        @Override
        public boolean isCellEditable(Object o) {
            return false;
        }

        @Override
        public @Nullable Object valueOf(Object o) {
            return o;
        }

        @Override
        public @Nullable Comparator<Object> getComparator() {
            return comparator;
        }

        static QColumnInfo[] of(c.Flip flip, boolean key) {
            final int length = flip.x.length;
            QColumnInfo[] res = new QColumnInfo[length];
            for (int i = 0; i < length; i++) {
                res[i] = new QColumnInfo(flip.x[i], flip.y[i].getClass().getComponentType(), key);
            }
            return res;
        }
    }
}
