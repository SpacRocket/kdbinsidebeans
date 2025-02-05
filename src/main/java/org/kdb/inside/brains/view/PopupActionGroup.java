package org.kdb.inside.brains.view;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PopupActionGroup extends DefaultActionGroup {
    public PopupActionGroup(@Nullable @NlsActions.ActionText String shortName, Icon icon) {
        super(shortName, true);
        getTemplatePresentation().setIcon(icon);
    }
}
