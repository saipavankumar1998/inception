/*
 * Copyright 2012
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
package de.tudarmstadt.ukp.inception.recommendation.render;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CHAIN_TYPE;

import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.TypeAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringStrategy;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.adapter.RecommendationSpanRenderer;
import de.tudarmstadt.ukp.inception.recommendation.adapter.RecommendationTypeRenderer;
import de.tudarmstadt.ukp.inception.recommendation.service.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.service.RecommendationService;

public class RecommendationRenderer
{
    /**
     * wrap JSON responses to BRAT visualizer
     *
     * @param vdoc
     *            A VDocument containing annotations for the given layer
     * @param aState
     *            the annotator state.
     * @param aJCas
     *            the JCas.
     * @param aAnnotationService
     *            the annotation service.s
     */
    public static void render(VDocument vdoc, AnnotatorState aState, JCas aJCas,
            AnnotationSchemaService aAnnotationService, RecommendationService recommendationService,
            LearningRecordService learningRecordService)
    {
        if (aJCas == null) {
            return;
        }
        
        for (AnnotationLayer layer : aState.getAnnotationLayers()) {
            if (layer.getName().equals(Token.class.getName())
                    || layer.getName().equals(Sentence.class.getName())
                    || (layer.getType().equals(CHAIN_TYPE)
                            && (aState.getMode().equals(Mode.AUTOMATION)
                                    || aState.getMode().equals(Mode.CORRECTION)
                                    || aState.getMode().equals(Mode.CURATION)))
                    || !layer.isEnabled()) { /* Hide layer if not enabled */
                continue;
            }
            
            ColoringStrategy coloringStrategy = ColoringStrategy.staticColor("#cccccc");  
            TypeAdapter adapter = aAnnotationService.getAdapter(layer);      
            RecommendationTypeRenderer renderer = getRenderer(adapter);
            if (renderer != null) {
                renderer.render(aJCas, vdoc, aState, coloringStrategy, recommendationService,
                        learningRecordService, layer);
            }
        }
    }
    
    /**
     * Helper method to fetch a renderer for a given type. This is indented to be a temporary
     * solution. The final solution should be able to return renderers specific to a certain
     * visualisation - one of which would be brat.
     */
    public static RecommendationTypeRenderer getRenderer(TypeAdapter aTypeAdapter) {
        if (aTypeAdapter instanceof SpanAdapter) {
            return new RecommendationSpanRenderer((SpanAdapter) aTypeAdapter);
        }
        return null;
    }
}