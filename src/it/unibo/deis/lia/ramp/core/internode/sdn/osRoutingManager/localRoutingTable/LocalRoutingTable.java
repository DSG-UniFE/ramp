package it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.localRoutingTable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class LocalRoutingTable {
    private int index;
    private String tableName;

    public LocalRoutingTable(int index, String tableName) {
        this.index = index;
        this.tableName = tableName;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
