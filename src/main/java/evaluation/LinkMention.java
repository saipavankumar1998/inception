package evaluation;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.Math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.LevenshteinDistance;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import de.dailab.irml.gerned.NewsReader;
import de.dailab.irml.gerned.QueriesReader;
import de.dailab.irml.gerned.data.Query;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordPosTagger;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordSegmenter;

public class LinkMention {

  private static Logger logger = LoggerFactory.getLogger(LinkMention.class);
  
  private static RepositoryConnection conn;
  
  // TODO
  private static String punctuations = "";
  private static String stopwords = "";

  private static String SPARQL_ENDPOINT = "http://knowledgebase.ukp.informatik.tu-darmstadt.de:8890/sparql";

  public static void main(String[] args) {

    initializeConnection();

    QueriesReader reader = new QueriesReader();
    // File queriesFile = new File("../gerned/dataset/ANY_german_queries.xml");
    File answersFile = new File("../gerned/dataset/ANY_german_queries_with_answers.xml");
    List<Query> queries = reader.readQueries(answersFile);

    Map<String, Set<String>> entities = new HashMap<String, Set<String>>();
    
    int counter = 0;
    for (Query query : queries) {
        String expected = mapWikipediaUrlToWikidataUrl(query.getEntity());
        Set<String> linkings = linkMention(query.getName());
        entities.put(query.getId(), linkings);
          if(linkings.contains(expected)) {
          counter++;
        }
           System.out.println(expected);
           System.out.println(linkings);
  }
    System.out.println(counter/queries.size());

  public static void initializeConnection()
  {
    SPARQLRepository repo = new SPARQLRepository(SPARQL_ENDPOINT);
    repo.initialize();
    conn = repo.getConnection();
  }

  public static String mapWikipediaUrlToWikidataUrl(String url) {
    String wikidataQueryString = QueryUtil.mapWikipediaUrlToWikidataUrlQuery(url);
    TupleQuery wikidataIdQuery = 
        conn.prepareTupleQuery(QueryLanguage.SPARQL, wikidataQueryString);
    try (TupleQueryResult wikidataIdResult = wikidataIdQuery.evaluate()) {
      while (wikidataIdResult.hasNext()) {
        BindingSet sol = wikidataIdResult.next();
        return sol.getValue("e2").toString();
      }
    } catch(Exception e) {
        logger.error("could not map wikipedia URL to Wikidata Id", e);
    }
    return null;
  }

  /*
   * Retrieves the sentence containing the mention as Tokens
   */
  public static List<Token> getMentionSentence(String docText, String mention) throws UIMAException{
    JCas doc = JCasFactory.createText(docText, "en");
    AnalysisEngineDescription desc = createEngineDescription(
        createEngineDescription(StanfordSegmenter.class),
        createEngineDescription(StanfordPosTagger.class, 
            StanfordSegmenter.PARAM_LANGUAGE_FALLBACK, "en"));
    AnalysisEngine pipeline = AnalysisEngineFactory.createEngine(desc);
    pipeline.process(doc);
    
    for (Sentence s : JCasUtil.select(doc, Sentence.class)) {
      List<Token> sentence = new LinkedList<>();
      boolean containsMention = false;
      for (Token t : JCasUtil.selectCovered(Token.class, s)) {
        sentence.add(t);
        if(t.getCoveredText().equals(mention)) {
          containsMention = true;
        }
      }
      if (containsMention) {
        for(Token t:sentence) System.out.print(t.getCoveredText());
        return sentence;
      }
    }
    return null;
}
  
  public static Set<Entity> linkMention(String mention) {
    Set<Entity> linkings = new HashSet<>();
    List<String> mentionArray = Arrays.asList(mention.split(" "));

    ListIterator<String> it = mentionArray.listIterator();
    String current;
    while (it.hasNext()) {
      current = it.next().toLowerCase();
      it.set(current);
      if (stopwords.contains(current) || punctuations.contains(current)) {
        it.remove();
      }
    }

    if (mentionArray.isEmpty()) {
      return null;
    }

    String entityQueryString = QueryUtil.entityQuery(mentionArray, 1000);
    TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, entityQueryString);
    try (TupleQueryResult entityResult = query.evaluate()) {
      while (entityResult.hasNext()) {
        BindingSet solution = entityResult.next();
            linkings.add(new Entity(solution.getValue("e2").toString(),
                    solution.getValue("label").toString(),
                    solution.getValue("anylabel").toString()));
      }
    } catch (QueryEvaluationException e) {
    	throw new QueryEvaluationException(e);
    }
    return linkings;
  }
  
  /**
   * Finds the position of a mention in a given sentence and
   * returns the corresponding tokens of the mention with 
   * <mentionContextSize> tokens before and 
   * <mentionContextSize> after the mention
   * 
   * TODO what happens if there are multiple mentions in a sentence?
   */
  public static List<Token> getMentionContext(List<Token> mentionSentence, List<Token> mention, 
		  int mentionContextSize)
  {
	  int start = 0, end = 0;
	  int j = 0;
	  boolean done = false;
	  while (done == false && j < mentionSentence.size()) {
		for (int i = 0; i < mention.size(); i++) {
	      if (!mentionSentence.get(j).getCoveredText().equals(mention.get(i).getCoveredText())) {
		 	break;
	      }
	      if (i == mention.size()-1) {
            start = j - (mention.size() - 1) - mentionContextSize;
            end = j + mentionContextSize + 1;
            done = true;
	      }
	    }
		j++;
	  }
	  
	  if (start == end) {
		  throw new IllegalStateException("Mention not found in sentence!");
	  }
	  if (start < 0) {
		  start = 0;
	  }
	  if (end > mentionSentence.size()) {
		  end = mentionSentence.size();
	  }
	  return mentionSentence.subList(start, end);
  }
  
  public static List<Token> tokenizeMention(String mention) throws UIMAException
  {
	  JCas doc = JCasFactory.createText(mention, "en");
	    AnalysisEngineDescription desc = createEngineDescription(
	        createEngineDescription(StanfordSegmenter.class),
				createEngineDescription(StanfordPosTagger.class,
						StanfordSegmenter.PARAM_LANGUAGE_FALLBACK, "en"));
		AnalysisEngine pipeline = AnalysisEngineFactory.createEngine(desc);
		pipeline.process(doc);

		List<Token> tokenizedMention = new LinkedList<>();
		for (Sentence s : JCasUtil.select(doc, Sentence.class)) {
			for (Token t : JCasUtil.selectCovered(Token.class, s)) {
				tokenizedMention.add(t);
			}
		}
		return tokenizedMention;
  }

private static String tokensToString(List<Token> sentence) {
	String result ="";
	  for(Token t: sentence) {
	    result.concat(t.getCoveredText());
	  }
	return result;
  }
  
  // TODO include relations
  // TODO filter against blacklist
  public static Set<String> getSemanticSignature(String wikidataId) {
      Set<String> semanticSignature = new HashSet<>();
      String queryString = QueryUtil.semanticSignatureQuery(wikidataId);
      TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
      try (TupleQueryResult result = query.evaluate()) {
        while (result.hasNext()) {
          BindingSet sol = result.next();
          semanticSignature.add(sol.getValue("label").toString());
        }
      } catch(Exception e) {
          logger.error("could not get semantic signature", e);
      }
      return semanticSignature;
  }

}