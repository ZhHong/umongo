/**
 *      Copyright (C) 2010 EdgyTech Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.edgytech.umongo;

import com.edgytech.swingfast.Application;
import com.edgytech.swingfast.ConfirmDialog;
import com.edgytech.swingfast.Frame;
import com.edgytech.swingfast.Scroller;
import com.edgytech.swingfast.TabbedDiv;
import com.edgytech.swingfast.Tree;
import com.edgytech.swingfast.XmlJComponentUnit;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.util.JSON;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.xml.sax.SAXException;

/**
 *
 * @author antoine
 */
public class UMongo extends Application implements Runnable {

    enum Item {

        workspace,
        workspaceScroll,
        tree,
        treeScroll,
        mainMenu,
        mainToolBar,
        frame,
        globalStore,
        docViewDialog,
        docTree,
        tabbedResult,
        jobBar,
    }
    public final static UMongo instance = new UMongo();
    private ArrayList<MongoNode> mongos = new ArrayList<MongoNode>();
    boolean stopped = false;
    BaseTreeNode node = null;
    
    FileWriter logWriter = null;
    boolean logFirstResult = false;

    public UMongo() {
        super(true);
        setEnumBinding(Item.values(), null);
    }

    Frame getFrame() {
        return (Frame) getBoundUnit(Item.frame);
    }

    Workspace getWorkspace() {
        return (Workspace) getBoundUnit(Item.workspace);
    }

    Scroller getWorkspaceScroll() {
        return (Scroller) getBoundUnit(Item.workspaceScroll);
    }

    Tree getTree() {
        return (Tree) getBoundUnit(Item.tree);
    }

    MainMenu getMainMenu() {
        return (MainMenu) getBoundUnit(Item.mainMenu);
    }

    AppPreferences getPreferences() {
        return getMainMenu().getPreferences();
    }

    MainToolBar getMainToolBar() {
        return (MainToolBar) getBoundUnit(Item.mainToolBar);
    }

    public void load() throws IOException, SAXException {
        xmlLoad(Resource.getXmlDir(), Resource.File.umongo, null);
    }

    public void loadSettings() throws IOException, SAXException {
        try {
            xmlLoad(Resource.getConfDir(), Resource.File.umongo, null);
        } catch (FileNotFoundException e) {
            // means no custom setting
        }
    }

    public void saveSettings() {
        try {
            xmlSave(Resource.getConfDir(), Resource.File.umongo, null);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public void initialize() {
        try {
            load();
            loadSettings();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, null, ex);
        }

        Thread maintenance = new Thread(this);
        maintenance.setDaemon(true);
        maintenance.start();
    }

    @Override
    public void wrapUp() {
        saveSettings();
    }

    public void start() {
    }

    public void stop() {
        stopped = true;
    }

    public static void main(String[] args) {
//        LogManager.getLogManager().getLogger("").setLevel(Level.FINE);
        
        instance.launch();
    }

    GlobalStore getGlobalStore() {
        return (GlobalStore) getBoundUnit(Item.globalStore);
    }

    void addMongo(Mongo mongo, List<String> dbs) throws MongoException, UnknownHostException {
        MongoNode node = new MongoNode(mongo, dbs);
        getTree().addChild(node);
        mongos.add(node);
        getTree().structureComponent();
        getTree().expandNode(node);
        getTree().selectNode(node);
    }

    void disconnect(MongoNode node) {
        mongos.remove(node);
        
        node.removeNode();
        Mongo mongo = ((MongoNode) node).getMongo();
        mongo.close();

        if (mongos.size() > 0) {
            MongoNode other = mongos.get(0);
            getTree().expandNode(other);
            getTree().selectNode(other);
        } else {
            displayElement(null);
        }

    }

    public ArrayList<MongoNode> getMongos() {
        return mongos;
    }

    void displayElement(XmlJComponentUnit unit) {
        getWorkspace().setContent(unit);
    }

    void showError(String in, Exception ex) {
        ErrorDialog dia = getGlobalStore().getErrorDialog();
        dia.setException(ex, in);
        dia.show();
    }

    TabbedDiv getTabbedResult() {
        return (TabbedDiv) getBoundUnit(Item.tabbedResult);
    }

    JobBar getJobBar() {
        return (JobBar) getBoundUnit(Item.jobBar);
    }
    
    public void displayNode(BaseTreeNode node) {
        this.node = node;
        BasePanel panel = null;
        if (node instanceof MongoNode)
            panel = getGlobalStore().getMongoPanel();
        else if(node instanceof DbNode)
            panel = getGlobalStore().getDbPanel();
        else if(node instanceof CollectionNode)
            panel = getGlobalStore().getCollectionPanel();
        else if(node instanceof IndexNode)
            panel = getGlobalStore().getIndexPanel();
        else if(node instanceof ServerNode)
            panel = getGlobalStore().getServerPanel();
        else if(node instanceof RouterNode)
            panel = getGlobalStore().getRouterPanel();
        else if(node instanceof ReplSetNode)
            panel = getGlobalStore().getReplSetPanel();

        panel.setNode(node);
        displayElement(panel);
    }

    public BaseTreeNode getNode() {
        return node;
    }
    
    public void runJob(DbJob job) {
        getJobBar().addJob(job);
        job.start();
    }

    public void removeJob(DbJob job) {
        getJobBar().removeJob(job);
    }

    long _nextTreeUpdate = System.currentTimeMillis();

    ConcurrentLinkedQueue<BaseTreeNode> nodesToRefresh = new ConcurrentLinkedQueue<BaseTreeNode>();

    void addNodeToRefresh(BaseTreeNode node) {
        nodesToRefresh.add(node);
    }

    
    public void run() {
        while (!stopped) {
            try {
                long now = System.currentTimeMillis();
                int treeRate = getPreferences().getTreeUpdateRate();
                if (treeRate > 0 && _nextTreeUpdate < now) {
                    _nextTreeUpdate = now + treeRate;
                    SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                            long start = System.currentTimeMillis();
                            // need structure here, to trigger refresh()
                            getTree().structureComponent();
                            getLogger().log(Level.FINE, "Tree update took " + (System.currentTimeMillis() - start));
//                           getTree().structureComponent();
                        }
                    });
                }
                
                BaseTreeNode node = null;
                while ((node = nodesToRefresh.poll()) != null) {
                    node.refresh();
                    final BaseTreeNode fnode = node;
                    SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                            fnode.updateComponent(false);
                        }
                    });
                }
                
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, null, ex);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                getLogger().log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        if (getJobBar().hasChildren()) {
            String text = "There are jobs running, force exit?";
            ConfirmDialog confirm = new ConfirmDialog(null, "Confirm Exit", null, text);
            if (!confirm.show())
                return;
        }
        super.windowClosing(e);
    }

    void updateLogging() {
        synchronized (this) {
            try {
                String logFile = getPreferences().getLogFile();
                if (logFile != null) {

                    logWriter = new FileWriter(logFile, true);
                    logFirstResult = getPreferences().getLogFirstResult();
                } else {
                    if (logWriter != null) {
                        logWriter.close();
                    }
                    logWriter = null;
                }
            } catch (IOException ex) {
                getLogger().log(Level.WARNING, null, ex);
            }
        }
    }
    
    boolean isLoggingOn() {
        return logWriter != null;
    }
    
    boolean isLoggingFirstResultOn() {
        return logFirstResult;
    }
    
    void logActivity(DBObject obj) {
        synchronized (this) {
            try {
                if ("Auth".equals(obj.get("name"))) {
                    // dont log auth
                    return;
                }
                
                logWriter.write(JSON.serialize(obj));
                logWriter.write("\n");
                logWriter.flush();
            } catch (IOException ex) {
                getLogger().log(Level.WARNING, null, ex);
            }
        }
    }
}
