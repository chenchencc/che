/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.java.client.editor;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.parts.PartPresenter;
import org.eclipse.che.ide.api.parts.PropertyListener;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPresenter;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceChangedEvent;
import org.eclipse.che.ide.ext.java.client.resource.SourceFolderMarker;

import java.util.HashSet;
import java.util.Set;

import static org.eclipse.che.ide.api.resources.ResourceDelta.DERIVED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.REMOVED;

/**
 * @author Evgen Vidolob
 */
@Singleton
public class FileWatcher {

    @Inject
    private EditorAgent editorAgent;

    private Set<TextEditorPresenter> editor2reconcile = new HashSet<>();

    @Inject
    private void handleFileOperations(EventBus eventBus) {

        eventBus.addHandler(ResourceChangedEvent.getType(), new ResourceChangedEvent.ResourceChangedHandler() {
            @Override
            public void onResourceChanged(ResourceChangedEvent event) {
                if (event.getDelta().getKind() != REMOVED) {
                    return;
                }

                if ((event.getDelta().getFlags() & DERIVED) == 0) {
                    return;
                }

                final Resource resource = event.getDelta().getResource();
                final Optional<Resource> srcFolder = resource.getParentWithMarker(SourceFolderMarker.ID);

                if (srcFolder.isPresent()) {
                    reparseAllOpenedFiles();
                }
            }
        });

        eventBus.addHandler(ActivePartChangedEvent.TYPE, new ActivePartChangedHandler() {
            @Override
            public void onActivePartChanged(ActivePartChangedEvent event) {
                if (event.getActivePart() instanceof TextEditorPresenter) {
                    if (editor2reconcile.contains(event.getActivePart())) {
                        reParseEditor((TextEditorPresenter<?>)event.getActivePart());
                    }
                }
            }
        });
    }

    private void reParseEditor(TextEditorPresenter<?> editor) {
        editor.refreshEditor();
        editor2reconcile.remove(editor);
    }

    public void editorOpened(final EditorPartPresenter editor) {
        final PropertyListener propertyListener = new PropertyListener() {
            @Override
            public void propertyChanged(PartPresenter source, int propId) {
                if (propId == EditorPartPresenter.PROP_DIRTY) {
                    if (!editor.isDirty()) {
                        reparseAllOpenedFiles();
                        //remove just saved editor
                        editor2reconcile.remove((TextEditorPresenter)editor);
                    }
                }
            }
        };
        editor.addPropertyListener(propertyListener);
    }

    private void reparseAllOpenedFiles() {
        for (EditorPartPresenter editorPartPresenter: editorAgent.getOpenedEditors()) {
            if (editorPartPresenter instanceof TextEditorPresenter) {
                final TextEditorPresenter< ? > editor = (TextEditorPresenter< ? >)editorPartPresenter;
                editor.refreshEditor();
            }
        }
    }
}
