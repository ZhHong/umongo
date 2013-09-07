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

import com.edgytech.swingfast.ConfirmDialog;
import com.edgytech.swingfast.XmlUnit;
import com.mongodb.DBRefBase;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bson.types.*;

/**
 *
 * @author antoine
 */
public class GlobalStore extends XmlUnit<XmlUnit> {

    enum Item {
        mongoPanel,
        dbPanel,
        collectionPanel,
        indexPanel,
        serverPanel,
        routerPanel,
        replSetPanel,
        optionDialog,
        errorDialog,
        exportDialog,
        importDialog,
        autoUpdateDialog,
        docBuilderDialog,
        lockingOperationDialog,
        documentMenu,
        documentFieldMenu,
        jsonTextDialog
    }

    public GlobalStore() {
        setEnumBinding(Item.values(), null);
    }

    public MongoPanel getMongoPanel() {
        return (MongoPanel) getBoundUnit(Item.mongoPanel);
    }

    public DbPanel getDbPanel() {
        return (DbPanel) getBoundUnit(Item.dbPanel);
    }

    public CollectionPanel getCollectionPanel() {
        return (CollectionPanel) getBoundUnit(Item.collectionPanel);
    }

    public IndexPanel getIndexPanel() {
        return (IndexPanel) getBoundUnit(Item.indexPanel);
    }

    public ServerPanel getServerPanel() {
        return (ServerPanel) getBoundUnit(Item.serverPanel);
    }

    public RouterPanel getRouterPanel() {
        return (RouterPanel) getBoundUnit(Item.routerPanel);
    }

    public ReplSetPanel getReplSetPanel() {
        return (ReplSetPanel) getBoundUnit(Item.replSetPanel);
    }

    OptionDialog getOptionDialog() {
        return (OptionDialog) getBoundUnit(Item.optionDialog);
    }

    ErrorDialog getErrorDialog() {
        return (ErrorDialog) getBoundUnit(Item.errorDialog);
    }

    ExportDialog getExportDialog() {
        return (ExportDialog) getBoundUnit(Item.exportDialog);
    }

    ImportDialog getImportDialog() {
        return (ImportDialog) getBoundUnit(Item.importDialog);
    }

    AutoUpdateDialog getAutoUpdateDialog() {
        return (AutoUpdateDialog) getBoundUnit(Item.autoUpdateDialog);
    }

    DocBuilderDialog getDocBuilderDialog() {
        return (DocBuilderDialog) getBoundUnit(Item.docBuilderDialog);
    }

    ConfirmDialog getLockingOperationDialog() {
        return (ConfirmDialog) getBoundUnit(Item.lockingOperationDialog);
    }

    JSONTextDialog getJSONTextDialog() {
        return (JSONTextDialog) getBoundUnit(Item.jsonTextDialog);
    }

    boolean confirmLockingOperation() {
        if (!getLockingOperationDialog().show())
            return false;
        return true;
    }
    
    public Object editValue(String key, Object value) {
        Class ceditor = null;
        if (value == null) {
            ceditor = null;
        } else if (value instanceof String) {
            ceditor = EditStringDialog.class;
        } else if (value instanceof Binary) {
            ceditor = EditBinaryDialog.class;
        } else if (value instanceof ObjectId || value instanceof DBRefBase) {
            ceditor = EditObjectIdDialog.class;
        } else if (value instanceof Boolean) {
            ceditor = EditBooleanDialog.class;
        } else if (value instanceof Code || value instanceof CodeWScope) {
            ceditor = EditCodeDialog.class;
        } else if (value instanceof Date) {
            ceditor = EditDateDialog.class;
        } else if (value instanceof Double || value instanceof Float) {
            ceditor = EditDoubleDialog.class;
        } else if (value instanceof Long || value instanceof Integer) {
            ceditor = EditLongDialog.class;
        } else if (value instanceof Pattern) {
            ceditor = EditPatternDialog.class;
        } else if (value instanceof BSONTimestamp) {
            ceditor = EditTimestampDialog.class;
        } else if (value instanceof UUID) {
            ceditor = EditUuidDialog.class;
        }

        if (ceditor == null)
            return null;
        EditFieldDialog editor = (EditFieldDialog) getFirstChildOfClass(ceditor, null);
        editor.setKey(key);
        editor.setValue(value);

        if (!editor.show()) {
            return value;
        }

        return editor.getValue();
    }
}
