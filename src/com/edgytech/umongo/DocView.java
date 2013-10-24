/**
 * Copyright (C) 2010 EdgyTech LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.edgytech.umongo;

import com.edgytech.swingfast.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author antoine
 */
public class DocView extends Zone implements EnumListener, TabInterface, Runnable {

    enum Item {

        docTree,
        tabDiv,
        tabTitle,
        tabClose,
        refresh,
        append,
        expandText,
        expandTextArea,
        indent,
        spawn,
        export,
        cursor,
        getMore,
        getAll,
        tools,
        startAutoUpdate,
        stopAutoUpdate,
        expandAll,
        collapseAll
    }
    Iterator<DBObject> iterator;
    DBCursor dbcursor;
    TabbedDiv tabbedDiv;
    Thread updateThread;
    String updateType;
    int updateInterval;
    int updateCount;
    boolean running;
    DbJob job;

    public DocView(String id, String label, DbJob job, Object root) {
        try {
            xmlLoad(Resource.getXmlDir(), Resource.File.docView, null);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, null, ex);
        }
        setEnumBinding(Item.values(), this);

        setId(id);
        setLabel(label);
        this.job = job;
        setStringFieldValue(Item.tabTitle, label);

        getTree().label = root.toString();

        if (job != null) {
            if (job.getButton() != null) {
                getComponentBoundUnit(Item.spawn).enabled = true;
            }

            ((MenuItem) getBoundUnit(Item.refresh)).enabled = true;
            ((MenuItem) getBoundUnit(Item.append)).enabled = true;
            ((MenuItem) getBoundUnit(Item.startAutoUpdate)).enabled = true;
        }
    }

    /**
     * create a doc view with static document
     *
     * @param id
     * @param label
     * @param job
     * @param root
     * @param doc
     */
    public DocView(String id, String label, DbJob job, Object root, DBObject doc) {
        this(id, label, job, root);

        if (doc != null) {
            addDocument(doc, job, true);
        }
    }

    /**
     * create a doc view with an iterator or a cursor
     *
     * @param id
     * @param label
     * @param job
     * @param root
     * @param iterator
     */
    public DocView(String id, String label, DbJob job, Object root, Iterator<DBObject> iterator) {
        this(id, label, job, root);

        if (iterator instanceof DBCursor) {
            this.dbcursor = (DBCursor) iterator;
            this.iterator = dbcursor;
            ((MenuItem) getBoundUnit(Item.refresh)).enabled = true;
            ((MenuItem) getBoundUnit(Item.startAutoUpdate)).enabled = true;
        } else {
            this.iterator = iterator;
        }
        getMore(null);
    }

//    /**
//     * create a doc view from a reusable command with existing result
//     * @param id
//     * @param label
//     * @param job
//     * @param db
//     * @param cmd
//     * @param result 
//     */
//    public DocView(String id, String label, DbJob job, DB db, DBObject cmd, DBObject result) {
//        this(id, label, job, db.getName() + ": " + cmd, result);
//        this.db = db;
//        this.cmd = cmd;
//
//        ((MenuItem) getBoundUnit(Item.startAutoUpdate)).enabled = true;
//        ((MenuItem) getBoundUnit(Item.refresh)).enabled = true;
//        ((MenuItem) getBoundUnit(Item.append)).enabled = true;
//        if (result == null) {
//            refresh();
//        }
//    }
//    
//    /**
//     * create a doc view to run a simple command against a database
//     * @param id
//     * @param label
//     * @param col
//     * @param panel 
//     * @param cmdStr 
//     */
//    public DocView(String id, String label, DB db, String cmdStr) {
//        this(id, label, null, db, new BasicDBObject(cmdStr, 1), null);
//    }
//    
//    /**
//     * create a doc view to run a simple command against a collection
//     * @param id
//     * @param label
//     * @param col
//     * @param cmdStr 
//     */
//    public DocView(String id, String label, DBCollection col, String cmdStr) {
//        this(id, label, null, col.getDB(), new BasicDBObject(cmdStr, col.getName()), null);
//    }
    Tree getTree() {
        return (Tree) getBoundUnit(Item.docTree);
    }

    public DBCursor getDBCursor() {
        return dbcursor;
    }

    public void close(ButtonBase button) {
        if (dbcursor != null) {
            dbcursor.close();
            dbcursor = null;
        }
        tabbedDiv.removeTab(this);
    }

    void addToTabbedDiv() {
        tabbedDiv = UMongo.instance.getTabbedResult();
        tabbedDiv.addTab(this, true);

        getTree().expandNode(getTree().getTreeNode());
    }

    public void actionPerformed(Enum enm, XmlComponentUnit unit, Object src) {
    }

    public void export(ButtonBase button) throws IOException {
        // export should be run in thread, to prevent concurrent mods
        ExportDialog dia = UMongo.instance.getGlobalStore().getExportDialog();
        if (!dia.show()) {
            return;
        }
        final DocumentSerializer ds = dia.getDocumentSerializer();
        try {
            DefaultMutableTreeNode root = getTree().getTreeNode();
            for (int i = 0; i < root.getChildCount(); ++i) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                Object obj = child.getUserObject();
                DBObject doc = null;
                if (obj instanceof DBObject) {
                    doc = (DBObject) obj;
                } else if (obj instanceof TreeNodeDocumentField) {
                    doc = (DBObject) ((TreeNodeDocumentField) obj).getValue();
                } else if (obj instanceof TreeNodeDocument) {
                    doc = ((TreeNodeDocument) obj).getDBObject();
                }
                if (doc != null) {
                    ds.writeObject(doc);
                }
            }
        } finally {
            ds.close();
        }
    }

    public Component getTabComponent() {
        return getComponentBoundUnit("tabDiv").getComponent();
    }

    public void startAutoUpdate(ButtonBase button) {
        AutoUpdateDialog dia = UMongo.instance.getGlobalStore().getAutoUpdateDialog();
        if (!dia.show()) {
            return;
        }

        if (updateThread != null) {
            stopAutoUpdate(null);
        }

        updateThread = new Thread(this);
        updateType = dia.getComponentStringFieldValue(AutoUpdateDialog.Item.autoType);
        updateInterval = dia.getComponentIntFieldValue(AutoUpdateDialog.Item.autoInterval);
        updateCount = dia.getComponentIntFieldValue(AutoUpdateDialog.Item.autoCount);
        running = true;
        updateThread.start();

        getComponentBoundUnit(Item.stopAutoUpdate).enabled = true;
        getComponentBoundUnit(Item.stopAutoUpdate).updateComponent();
    }

    public void stopAutoUpdate(ButtonBase button) {
        running = false;
        try {
            updateThread.interrupt();
            updateThread.join();
        } catch (InterruptedException ex) {
        }
        updateThread = null;

        getComponentBoundUnit(Item.stopAutoUpdate).enabled = false;
        getComponentBoundUnit(Item.stopAutoUpdate).updateComponent();
    }

    public void run() {
        int i = 0;
        while (running) {
            try {
                DbJob job = null;
                if ("Refresh".equals(updateType) || dbcursor != null) {
                    job = getRefreshJob();
                } else if ("Append".equals(updateType)) {
                    job = getAppendJob();
                }
                final DbJob fjob = job;

                SwingUtilities.invokeAndWait(new Runnable() {

                    @Override
                    public void run() {
                        fjob.addJob();
                    }
                });

                try {
                    fjob.join();
                } catch (InterruptedException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                } catch (ExecutionException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                }

                if (updateCount > 0 && ++i >= updateCount) {
                    break;
                }

                Thread.sleep(updateInterval * 1000);
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, null, ex);
            }
        }
        getLogger().log(Level.INFO, "Ran " + i + " updates");
    }

    public void refresh(ButtonBase button) {
        getRefreshJob().addJob();
    }

    public DbJob getRefreshJob() {
        if (dbcursor != null) {
            return getUpdateCursorJob();
        } else {
            return getRefreshJob(false);
        }
    }

    public void append(ButtonBase button) {
        getAppendJob().addJob();
    }

    public DbJob getAppendJob() {
        return getRefreshJob(true);
    }

    public void spawn(ButtonBase button) {
        if (job != null) {
            job.spawnDialog();
        }
    }

    public void expandText(ButtonBase button) throws IOException {
        FormDialog dia = (FormDialog) button.getDialog();

        DefaultMutableTreeNode root = getTree().getTreeNode();
        DefaultMutableTreeNode select = getTree().getSelectionNode();
        String txt = "";
        if (select == null || select == root) {
            txt = getTextAllNodes(false);
        } else {
            txt = getTextForNode(select.getUserObject(), false);
        }
        setStringFieldValue(Item.expandTextArea, txt);
        dia.label = this.label;

        dia.show();
    }

    String getTextAllNodes(boolean indent) throws IOException {
        StringBuilder b = new StringBuilder();
        DefaultMutableTreeNode root = getTree().getTreeNode();
        for (int i = 0; i < root.getChildCount(); ++i) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object obj = child.getUserObject();
            b.append(getTextForNode(obj, indent));
        }
        return b.toString();
    }

    String getTextForNode(Object obj, boolean indent) throws IOException {
        DBObject doc = null;
        if (obj instanceof DBObject) {
            doc = (DBObject) obj;
        } else if (obj instanceof TreeNodeDocumentField) {
            doc = (DBObject) ((TreeNodeDocumentField) obj).getValue();
        } else if (obj instanceof TreeNodeDocument) {
            doc = ((TreeNodeDocument) obj).getDBObject();
        }
        if (doc == null) {
            return "";
        }

        final DocumentSerializer ds = new DocumentSerializer(DocumentSerializer.Format.JSON, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        ds.setOutputStream(baos);
        try {
            ds.writeObject(doc);
        } finally {
            ds.close();
        }

        String txt = new String(baos.toByteArray());

        if (indent) {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(txt);
            txt = gson.toJson(je);
            // add newline in case there are following docs
            txt += "\n";
        }
        return txt;
    }

    public void indent(ButtonBase button) throws IOException {
        DefaultMutableTreeNode root = getTree().getTreeNode();
        DefaultMutableTreeNode select = getTree().getSelectionNode();
        String txt = "";
        if (select == null || select == root) {
            txt = getTextAllNodes(true);
        } else {
            txt = getTextForNode(select.getUserObject(), true);
        }
        setComponentStringFieldValue(Item.expandTextArea, txt);
    }

    public DbJob getRefreshJob(final boolean append) {
        if (job == null) {
            return null;
        }

        DbJob newJob = new DbJob() {

            BasicDBObject result;

            @Override
            public Object doRun() throws Exception {
                result = (BasicDBObject) job.doRun();
                return null;
            }

            @Override
            public String getNS() {
                return job.getNS();
            }

            @Override
            public String getShortName() {
                return job.getShortName();
            }

            @Override
            public void wrapUp(Object res) {
                super.wrapUp(res);
                if (res == null && result != null) {
                    if (!append) {
                        getTree().removeAllChildren();
                    }
                    addDocument(result, this);
                    getTree().structureComponent();
                    // result of command should be fully expanded
                    getTree().expandAll();

                    // panel info may need to be refreshed
                    if (job.getPanel() != null) {
                        job.getPanel().refresh();
                    }
                }
            }
        };

        return newJob;
    }

    public void getMore(final int max) {
        new DbJob() {

            @Override
            public Object doRun() throws IOException {
                int i = 0;
                while (iterator.hasNext() && (i++ < max || max <= 0)) {
                    DBObject obj = iterator.next();
                    if (obj == null) {
                        break;
                    }
                    addDocument(obj, null);
                }
                return null;
            }

            @Override
            public String getNS() {
                if (dbcursor != null) {
                    return dbcursor.getCollection().getFullName();
                }
                return getLabel();
            }

            @Override
            public String getShortName() {
                return "Find";
            }

            @Override
            public void wrapUp(Object res) {
                super.wrapUp(res);
                if (res == null) {
                    // res should be null
                    // should have a cursor id now
                    if (dbcursor != null) {
                        BasicDBObject desc = getDescription(DocView.this.job.getRoot(dbcursor));
                        getTree().label = desc.toString();
                    }
                    getTree().structureComponent();
                    getTree().expandNode(getTree().getTreeNode());

                    DocView.this.updateButtons();
                }
            }
        }.addJob();
    }

    public void getMore(ButtonBase button) {
        getMore(UMongo.instance.getPreferences().getGetMoreSize());
    }

    public void getAll(ButtonBase button) {
        getMore(0);
    }

    public DefaultMutableTreeNode getSelectedNode() {
        TreePath path = getTree().getSelectionPath();
        if (path == null || path.getPathCount() < 2) {
            return null;
        }
        return (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    public DBObject getSelectedDocument() {
        TreePath path = getTree().getSelectionPath();
        if (path == null || path.getPathCount() < 2) {
            return null;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(1);
        Object obj = node.getUserObject();
        if (obj instanceof TreeNodeDocumentField) {
            return (DBObject) ((TreeNodeDocumentField) obj).getValue();
        } else if (obj instanceof DBObject) {
            return (DBObject) obj;
        } else if (obj instanceof TreeNodeDocument) {
            return ((TreeNodeDocument) obj).getDBObject();
        }
        return null;
    }

    String getSelectedDocumentPath() {
        TreePath path = getTree().getSelectionPath();
        String pathStr = "";
        if (path.getPathCount() < 2) {
            return null;
        }
        for (int i = 2; i < path.getPathCount(); ++i) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(i);
            String key = ((TreeNodeDocumentField) node.getUserObject()).getKey();
            pathStr += "." + key;
        }
        return pathStr.substring(1);
    }

    public DbJob getUpdateCursorJob() {
        if (dbcursor == null) {
            return null;
        }
//        final int count = getTree().getChildren().size();
        DbJob newJob = new DbJob() {

            @Override
            public Object doRun() throws Exception {
                dbcursor = (DBCursor) job.doRun();
                return null;
            }

            @Override
            public String getNS() {
                return job.getNS();
            }

            @Override
            public String getShortName() {
                return job.getShortName();
            }

            @Override
            public void wrapUp(Object res) {
                super.wrapUp(res);
                if (res == null) {
                    iterator = dbcursor;
                    getTree().removeAllChildren();
                    getMore(null);
                }
            }
        };

        return newJob;
    }

    private void updateButtons() {
        boolean canGetMore = false;
        if (dbcursor != null) {
            // can always get more from tailable cursor
            canGetMore = iterator.hasNext() || dbcursor.getCursorId() > 0;
        } else {
            canGetMore = iterator.hasNext();
        }
        getComponentBoundUnit(Item.getMore).enabled = canGetMore;
        getComponentBoundUnit(Item.getMore).updateComponent();
        getComponentBoundUnit(Item.getAll).enabled = canGetMore;
        getComponentBoundUnit(Item.getAll).updateComponent();

    }

    public void addDocument(DBObject doc, DbJob job) {
        addDocument(doc, job, false);
    }

    public void addDocument(DBObject doc, DbJob job, boolean expand) {
        TreeNodeLabel node = new TreeNodeDocument(doc, job);
        getTree().addChild(node);
        if (expand) {
            getTree().expandNode(node);
        }
    }

//    protected void appendDoc(DBObject doc) {
//        TreeNodeLabel node = new TreeNodeLabel();
//        node.forceTreeNode(MongoUtils.dbObjectToTreeNode(doc));
//        getTree().addChild(node);
//    }
    public void collapseAll(ButtonBase button) {
        getTree().collapseAll();
        // need to reexpand root
        getTree().expandNode(getTree().getTreeNode());
    }

    public void expandAll(ButtonBase button) {
        getTree().expandAll();
    }
}
