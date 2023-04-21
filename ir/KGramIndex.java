package ir;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import ir.Query.QueryTerm;

import java.nio.charset.StandardCharsets;


public class KGramIndex {

    /** Mapping from term ids to actual term strings */
    HashMap<Integer,String> id2term = new HashMap<Integer,String>();

    /** Mapping from term strings to term ids */
    HashMap<String,Integer> term2id = new HashMap<String,Integer>();

    /** Mapping from term ids to number of k-grams of this term */
    HashMap<Integer, Integer> id2num = new HashMap<Integer, Integer>();

    /** Index from k-grams to list of term ids that contain the k-gram */
    HashMap<String,List<KGramPostingsEntry>> index = new HashMap<String,List<KGramPostingsEntry>>();

    /** The ID of the last processed term */
    int lastTermID = -1;

    /** Number of symbols to form a K-gram */
    int K = 3;

    public KGramIndex(int k) {
        K = k;
        if (k <= 0) {
            System.err.println("The K-gram index can't be constructed for a negative K value");
            System.exit(1);
        }
    }

    /** Generate the ID for an unknown term */
    private int generateTermID() {
        return ++lastTermID;
    }

    public int getK() {
        return K;
    }


    /**
     *  Get intersection of two postings lists
     */
    public List<KGramPostingsEntry> intersect(List<KGramPostingsEntry> p1, List<KGramPostingsEntry> p2) {
        // 
        // YOUR CODE HERE
        //
        List<KGramPostingsEntry> pResult = new ArrayList<>();
        if (p1 == null || p2 == null) {
            return pResult;
        }

        int i1 = 0, i2 = 0;
        while (i1 < p1.size() && i2 < p2.size()) {
            if (p1.get(i1).tokenID < p2.get(i2).tokenID) {
                i1++;
            }
            else if (p1.get(i1).tokenID > p2.get(i2).tokenID) {
                i2++;
            }
            else {
                pResult.add(new KGramPostingsEntry(p1.get(i1)));
                i1++;
                i2++;
            }
        }
        return pResult;
    }


    /** Inserts all k-grams from a token into the index. */
    public void insert( String token ) {
        //
        // YOUR CODE HERE
        //
        int id;
        if (!term2id.containsKey(token)) {
            id = generateTermID();
            term2id.put(token, id);
            id2term.put(id, token);
        }
        else return;

        String symbolizedToken = "^" + token + "$";

        id2num.put(id, symbolizedToken.length() - K + 1);

        if (symbolizedToken.length() < K) {
            if (index.containsKey(token)) {
                index.get(token).add(new KGramPostingsEntry(id));
            }
            else {
                ArrayList<KGramPostingsEntry> newEntry = new ArrayList<>();
                newEntry.add(new KGramPostingsEntry(id));
                index.put(token, newEntry);
            }
            return;
        }
        
        for (int i = 0; i < symbolizedToken.length() - K + 1; i++) {
            String kGram = symbolizedToken.substring(i, i + K);
            if (index.containsKey(kGram)) {
                if (index.get(kGram).get(index.get(kGram).size() - 1).tokenID != id) {
                    index.get(kGram).add(new KGramPostingsEntry(id));
                }
            }
            else {
                ArrayList<KGramPostingsEntry> newEntry = new ArrayList<>();
                newEntry.add(new KGramPostingsEntry(id));
                index.put(kGram, newEntry);
            }
        }
    }

    public List<ArrayList<QueryTerm>> parseWildcard(ArrayList<QueryTerm> oriQueryterm, Query q) {
        ArrayList<ArrayList<QueryTerm>> possibleCandidates = new ArrayList<>();
        for (int i = 0; i < oriQueryterm.size(); i++) {
            possibleCandidates.add(new ArrayList<>());
            if (isWildcard(oriQueryterm.get(i).term)) {
                // pray for result not being null :(
                List<KGramPostingsEntry> result = removeFalsePositive(postingsFromKgrams(kgramsFromWildcard(oriQueryterm.get(i).term)), oriQueryterm.get(i).term);
                for (KGramPostingsEntry entry : result) {
                    possibleCandidates.get(i).add(q.new QueryTerm(id2term.get(entry.tokenID), 1.0));
                }
            }
            else {
                possibleCandidates.get(i).add(q.new QueryTerm(oriQueryterm.get(i).term, 1.0));
            }
        }

        return possibleCandidates;
    }

    public List<ArrayList<QueryTerm>> enumerateCandidates(List<ArrayList<QueryTerm>> sets, Query q) {
        if (sets.size() == 1) {
            List<ArrayList<QueryTerm>> result = new ArrayList<ArrayList<QueryTerm>>();
            for (int i = 0; i < sets.get(0).size(); i++) {
                result.add(new ArrayList<QueryTerm>());
                result.get(i).add(q.new QueryTerm(sets.get(0).get(i).term, 1.0));
            }
            return result;
        } 
        else {
            ArrayList<ArrayList<QueryTerm>> result = new ArrayList<>();
            ArrayList<QueryTerm> firstSet = sets.get(0);
            List<ArrayList<QueryTerm>> remainingSets = sets.subList(1, sets.size());
            List<ArrayList<QueryTerm>> suffixes = enumerateCandidates(remainingSets, q);
            for (QueryTerm element : firstSet) {
                for (ArrayList<QueryTerm> suffix : suffixes) {
                    ArrayList<QueryTerm> temp = new ArrayList<>(suffix);
                    temp.add(0, element);
                    result.add(temp);
                }
            }
            return result;
        }
    }

    public List<String> kgramsFromWildcard(String wildcard) {
        List<String> kgramsList = new ArrayList<>();

        String symbolizedWildcard = "^" + wildcard + "$";

        String[] parts = symbolizedWildcard.split("\\*");
        for (String part : parts) {
            for (int i = 0; i < part.length() - K + 1; i++) {
                kgramsList.add(part.substring(i, i + K));
            }
        }
        return kgramsList;
    }

    public List<KGramPostingsEntry> postingsFromKgrams(List<String> kgrams) {
        List<KGramPostingsEntry> result = null;
        for (int i = 0; i < kgrams.size(); i++) {
            if (i == 0) {
                result = getPostings(kgrams.get(i));
            }
            else {
                result = intersect(result, getPostings(kgrams.get(i)));
            }
        }
        return result;
    }

    public List<KGramPostingsEntry> removeFalsePositive(List<KGramPostingsEntry> intersectResult, String wildcard) {
        List<KGramPostingsEntry> result = new ArrayList<>();
        for (KGramPostingsEntry entry : intersectResult) {
            if (Pattern.matches(wildcard.replace("*", ".*"), id2term.get(entry.tokenID))) {
                result.add(new KGramPostingsEntry(entry));
            }
        }
        return result;
    }

    public boolean isWildcard(String token) {
        return token.contains("*");
    }

    /** Get postings for the given k-gram */
    public List<KGramPostingsEntry> getPostings(String kgram) {
        return index.get(kgram);
    }

    /** Get id of a term */
    public Integer getIDByTerm(String term) {
        return term2id.get(term);
    }

    /** Get a term by the given id */
    public String getTermByID(Integer id) {
        return id2term.get(id);
    }

    private static HashMap<String,String> decodeArgs( String[] args ) {
        HashMap<String,String> decodedArgs = new HashMap<String,String>();
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("patterns_file", args[i++]);
                }
            } else if ( "-f".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("file", args[i++]);
                }
            } else if ( "-k".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("k", args[i++]);
                }
            } else if ( "-kg".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("kgram", args[i++]);
                }
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }
        return decodedArgs;
    }

    public static void main(String[] arguments) throws FileNotFoundException, IOException {
        HashMap<String,String> args = decodeArgs(arguments);

        int k = Integer.parseInt(args.getOrDefault("k", "3"));
        KGramIndex kgIndex = new KGramIndex(k);

        File f = new File(args.get("file"));
        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
        Tokenizer tok = new Tokenizer( reader, true, false, true, args.get("patterns_file") );
        while ( tok.hasMoreTokens() ) {
            String token = tok.nextToken();
            kgIndex.insert(token);
        }

        String[] kgrams = args.get("kgram").split(" ");
        List<KGramPostingsEntry> postings = null;
        for (String kgram : kgrams) {
            if (kgram.length() != k) {
                System.err.println("Cannot search k-gram index: " + kgram.length() + "-gram provided instead of " + k + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(kgram);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(kgram));
            }
        }
        if (postings == null) {
            System.err.println("Found 0 posting(s)");
        } else {
            int resNum = postings.size();
            System.err.println("Found " + resNum + " posting(s)");
            if (resNum > 10) {
                System.err.println("The first 10 of them are:");
                resNum = 10;
            }
            for (int i = 0; i < resNum; i++) {
                System.err.println(kgIndex.getTermByID(postings.get(i).tokenID));
            }
        }
    }
}
