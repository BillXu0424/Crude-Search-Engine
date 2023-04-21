package ir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
        String term;
        double weight;
        QueryTerm( String t, double w ) {
            term = t;
            weight = w;
        }
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.2;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
        StringTokenizer tok = new StringTokenizer( queryString );
        while ( tok.hasMoreTokens() ) {
            queryterm.add( new QueryTerm(tok.nextToken(), 1.0) );
        }    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
        return queryterm.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for ( QueryTerm t : queryterm ) {
            len += t.weight; 
        }
        return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for ( QueryTerm t : queryterm ) {
            queryCopy.queryterm.add( new QueryTerm(t.term, t.weight) );
        }
        return queryCopy;
    }
    
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Engine engine ) {
        //
        //  YOUR CODE HERE
        //

        HashMap<String, Double> newQueryVectors = new HashMap<>();
        HashSet<Integer> relevantDocs = new HashSet<>();

        for (int i = 0; i < docIsRelevant.length; i++) { 
            if (docIsRelevant[i]) {
                relevantDocs.add(i);
            }
        }

        if (relevantDocs.isEmpty()) return;

        for (QueryTerm singleQt : queryterm) {
            if (newQueryVectors.containsKey(singleQt.term)) {
                newQueryVectors.put(singleQt.term, newQueryVectors.get(singleQt.term) + alpha * idf(singleQt.term, engine));
            }
            else {
                newQueryVectors.put(singleQt.term, alpha * idf(singleQt.term, engine));
            }
        }

        for (int i : relevantDocs) {
            File f = new File(engine.index.docNames.get(results.get(i).docID));
            try {
                Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
                Tokenizer tok = new Tokenizer(reader, true, false, true, engine.patterns_file);

                while ( tok.hasMoreTokens() ) {
                    String token = tok.nextToken();
                    if (newQueryVectors.containsKey(token)) {
                        newQueryVectors.put(token, newQueryVectors.get(token) + beta / relevantDocs.size() * idf(token, engine));
                    }
                    else {
                        newQueryVectors.put(token, beta / relevantDocs.size() * idf(token, engine));
                    }
                }
                reader.close();
            }
            catch ( IOException e ) {
                System.err.println("Warning: IOException during reading files.");
            }
        }

        queryterm = new ArrayList<QueryTerm>();
        for (Map.Entry<String, Double> entry : newQueryVectors.entrySet()) {
            QueryTerm newSingleQt = new QueryTerm(entry.getKey(), entry.getValue());
            queryterm.add(newSingleQt);
        }

    }

    public double idf(String token, Engine engine) {
        return Math.log(engine.index.docNames.size() * 1.0 / engine.index.getPostings(token).size());
    }
}


