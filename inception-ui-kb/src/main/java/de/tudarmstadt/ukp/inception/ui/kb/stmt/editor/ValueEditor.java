/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.ui.kb.stmt.editor;

import org.apache.wicket.markup.html.form.FormComponentPanel;

import de.tudarmstadt.ukp.inception.ui.kb.stmt.Focusable;

public abstract class ValueEditor<T> extends FormComponentPanel<T> implements Focusable {

    private static final long serialVersionUID = 6386684203515199433L;

    public ValueEditor(String id) {
        super(id);
    }

}
