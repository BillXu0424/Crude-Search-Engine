package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ir.Query.QueryTerm;

public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        //
        // YOUR CODE HERE
        //
        double result = intersection * 1.0 / (szA + szB - intersection);
        return result;
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
        //
        // YOUR CODE HERE
        //
        int l1 = s1.length(), l2 = s2.length();
        int[][] dp = new int[l1 + 1][l2 + 1];
        
        for (int i = 0; i <= l1; i++) {
            for (int j = 0; j <= l2; j++) {
                if (Math.min(i, j) == 0) {
                    dp[i][j] = Math.max(i, j);
                    continue;
                }
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                }
                else {
                    int result = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1);
                    result = Math.min(result, dp[i - 1][j - 1] + 2);
                    dp[i][j] = result;
                }
            }
        }
        return dp[l1][l2];
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        //
        // YOUR CODE HERE
        //
        ArrayList<ArrayList<KGramStat>> candidatesList = new ArrayList<>();
        

        for (QueryTerm singleQt : query.queryterm) {
            HashSet<Integer> triedTerms = new HashSet<>();
            HashSet<String> kgramsQuery = new HashSet<>(kgIndex.kgramsFromWildcard(singleQt.term));
            ArrayList<KGramStat> candidates = new ArrayList<>();
            for (String kgram : kgramsQuery) {
                for (KGramPostingsEntry entry : kgIndex.getPostings(kgram)) {
                    if (triedTerms.contains(entry.tokenID)) continue;
                    triedTerms.add(entry.tokenID);
                    /* jaccard */
                    int intersection = 0;
                    List<String> kgramsCandidate = new ArrayList<>(kgIndex.kgramsFromWildcard(kgIndex.id2term.get(entry.tokenID)));
                    for (String kgramCandidate : kgramsCandidate) {
                        if (kgramsQuery.contains(kgramCandidate)) {
                            intersection++;
                        }
                    }
                    double jaccard = jaccard(kgramsQuery.size(), kgIndex.id2num.get(entry.tokenID), intersection);
                    if (jaccard < JACCARD_THRESHOLD) continue;
                    /* levenstein */
                    String token = kgIndex.id2term.get(entry.tokenID);
                    if (editDistance(singleQt.term, token) <= MAX_EDIT_DISTANCE) {
                        candidates.add(new KGramStat(token, index.getPostings(token).size() * jaccard));
                    }
                }
            }
            if (candidates.isEmpty()) return null;
            Collections.sort(candidates, Collections.reverseOrder());
            candidatesList.add(candidates);
        }
            
        List<KGramStat> resultList = mergeCorrections(candidatesList, limit);
        int resultNum = Math.min(limit, resultList.size());
        String[] result = new String[resultNum];
        for (int i = 0; i < resultNum; i++) {
            result[i] = resultList.get(i).token;
        }
        return result;
    }

    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(ArrayList<ArrayList<KGramStat>> qCorrections, int limit) {
        //
        // YOUR CODE HERE
        //
        List<KGramStat> result = null;
        for (int i = 0; i < qCorrections.size(); i++) {
            if (i == 0) {
                result = new ArrayList<>(qCorrections.get(0));
                int resultNum = Math.min(limit, result.size());
                result = result.subList(0, resultNum);
            }
            else {
                ArrayList<KGramStat> tempList = new ArrayList<>();
                for (KGramStat stat1 : result) {
                    for (KGramStat stat2 : qCorrections.get(i)) {
                        tempList.add(new KGramStat(stat1.token + " " + stat2.token, stat1.score + stat2.score));
                    }
                }
                Collections.sort(tempList, Collections.reverseOrder());
                int resultNum = Math.min(limit, tempList.size());
                result = tempList.subList(0, resultNum);
            }
        }
        return result;
    }
}
