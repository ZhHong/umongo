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

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.XmlComponentUnit;
import com.mongodb.DBObject;
import java.io.IOException;
import javax.swing.JPanel;
import com.edgytech.umongo.IndexPanel.Item;

/**
 *
 * @author antoine
 */
public class IndexPanel extends BasePanel implements EnumListener<Item> {

    enum Item {

        icon,
        name,
        ns,
        key,
        info,
        stats,
        refresh,
        drop,
    }

    public IndexPanel() {
        setEnumBinding(Item.values(), this);
    }

    public IndexNode getIndexNode() {
        return (IndexNode) getNode();
    }

    @Override
    protected void updateComponentCustom(JPanel comp) {
        try {
            DBObject index = getIndexNode().getIndex();
            setStringFieldValue(Item.name, (String) index.get("name"));
            setStringFieldValue(Item.ns, (String) index.get("ns"));
            ((DocField) getBoundUnit(Item.key)).setDoc((DBObject) index.get("key"));
            ((DocField) getBoundUnit(Item.info)).setDoc(index);
        } catch (Exception e) {
            UMongo.instance.showError(this.getClass().getSimpleName() + " update", e);
        }
    }

    public void actionPerformed(Item enm, XmlComponentUnit unit, Object src) {
    }

    public void drop(ButtonBase button) {
        final IndexNode indexNode = getIndexNode();
        new DbJob() {

            @Override
            public Object doRun() throws IOException {
                indexNode.getCollectionNode().getCollection().dropIndex(indexNode.getName());
                return null;
            }

            @Override
            public String getNS() {
                return getIndexNode().getIndexedCollection().getFullName();
            }

            @Override
            public String getShortName() {
                return "Drop Index";
            }

            @Override
            public DBObject getRoot(Object result) {
                return indexNode.getIndex();
            }

            @Override
            public void wrapUp(Object res) {
                super.wrapUp(res);
                indexNode.removeNode();
            }
        }.addJob();
    }

    public void stats(ButtonBase button) {
        new DbJobCmd(getIndexNode().getStatsCollection(), "collstats").addJob();
    }
}
