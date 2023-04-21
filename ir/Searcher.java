package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import ir.Query.QueryTerm;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    /** The HITS ranker */
    HITSRanker hitsRanker;
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.hitsRanker = new HITSRanker("../pagerank/linksDavis.txt", "../pagerank/davisTitles.txt", this.index);
    }

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType, NormalizationType normType ) { 
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        /* Boolean indexing */
        if (queryType == QueryType.INTERSECTION_QUERY || queryType == QueryType.PHRASE_QUERY) {
            return intersect(query.queryterm, queryType, query);
        }
        /* ranked query */
        else if (queryType == QueryType.RANKED_QUERY) {
            if (rankingType == RankingType.TF_IDF) {
                return tfIdfSearch(query.queryterm, normType, query);
            }
            else if (rankingType == RankingType.PAGERANK) {
                return pageRankSearch(query.queryterm, normType, query);
            }
            else if (rankingType == RankingType.COMBINATION) {
                return combinedSearch(query.queryterm, normType, query);
            }
            else if (rankingType == RankingType.HITS) {
                return hitsRankSearch(query.queryterm, query);
            }
            else {
                throw new IllegalArgumentException("No such RankingType!");
            }
        }
        else {
            throw new IllegalArgumentException("No such QueryType!");
        }
    }

    public PostingsList hitsRankSearch(ArrayList<QueryTerm> qt, Query query) {
        HashSet<Integer> tempEntry = new HashSet<>();

        List<ArrayList<QueryTerm>> possibleCandidates;
        boolean isWildcard = false;
        for (QueryTerm singleQt : qt) {
            if (this.kgIndex.isWildcard(singleQt.term)) {
                isWildcard = true;
                break;
            }
        }
        if (isWildcard) {
            possibleCandidates = kgIndex.parseWildcard(qt, query);
        }
        else {
            possibleCandidates = new ArrayList<>();
            for (QueryTerm singleQt : qt) {
                ArrayList<QueryTerm> temp = new ArrayList<>();
                temp.add(singleQt);
                possibleCandidates.add(temp);
            }
        }

        // List<ArrayList<QueryTerm>> possibleCandidates = kgIndex.parseWildcard(qt, query);

        for (ArrayList<QueryTerm> qtList : possibleCandidates) {
            for (QueryTerm term : qtList) {
                PostingsList pl = index.getPostings(term.term);
                for (int i = 0; i < pl.size(); i++) {
                    tempEntry.add(pl.get(i).docID);
                }
            }
        }
        
        PostingsList rootPost = new PostingsList();
        for (int id : tempEntry) {
            rootPost.appendEntry(new PostingsEntry(id));
        }
        PostingsList result = this.hitsRanker.rank(rootPost);
        return result;
    }

    public PostingsList tfIdfSearch(ArrayList<QueryTerm> qt, NormalizationType normType, Query query) {
        List<ArrayList<QueryTerm>> possibleCandidates;
        boolean isWildcard = false;
        for (QueryTerm singleQt : qt) {
            if (this.kgIndex.isWildcard(singleQt.term)) {
                isWildcard = true;
                break;
            }
        }
        if (isWildcard) {
            possibleCandidates = kgIndex.parseWildcard(qt, query);
        }
        else {
            possibleCandidates = new ArrayList<>();
            for (QueryTerm singleQt : qt) {
                ArrayList<QueryTerm> temp = new ArrayList<>();
                temp.add(singleQt);
                possibleCandidates.add(temp);
            }
        }

        // List<ArrayList<QueryTerm>> possibleCandidates = kgIndex.parseWildcard(qt, query);

        HashMap<Integer, PostingsEntry> docScore = new HashMap<>();

        for (ArrayList<QueryTerm> qtList : possibleCandidates) {
            for (QueryTerm term : qtList) {
                PostingsList pl = index.getPostings(term.term);
                if (pl == null) continue;
    
                for (int i = 0; i < pl.size(); i++) {
                    PostingsEntry pe = pl.get(i);
                    double tf_idf = tf_idf(pl, pe) * term.weight;
              
                    if (docScore.containsKey(pe.docID)) {
                        docScore.get(pe.docID).score += tf_idf;
                    }
                    else {
                        docScore.put(pe.docID, new PostingsEntry(pe.docID, tf_idf));
                    }
                }
            }
        }

        ArrayList<PostingsEntry> answerPlList = new ArrayList<>(docScore.values());
        for (PostingsEntry pe : answerPlList) {
            if (normType == NormalizationType.NUMBER_OF_WORDS) {
                pe.score /= index.docLengths.get(pe.docID);
            }
            else if (normType == NormalizationType.EUCLIDEAN) {
                pe.score /= index.l2Lengths.get(pe.docID);
            }
            else {
                throw new IllegalArgumentException("No such normalization type!");
            }
        }
        Collections.sort(answerPlList, new PostingsEntry.scoreComparator());
        
        return new PostingsList(answerPlList);
    }

    public PostingsList pageRankSearch(ArrayList<QueryTerm> qt, NormalizationType normType, Query query) {
        // List<ArrayList<QueryTerm>> possibleCandidates = kgIndex.parseWildcard(qt, query);

        List<ArrayList<QueryTerm>> possibleCandidates;
        boolean isWildcard = false;
        for (QueryTerm singleQt : qt) {
            if (this.kgIndex.isWildcard(singleQt.term)) {
                isWildcard = true;
                break;
            }
        }
        if (isWildcard) {
            possibleCandidates = kgIndex.parseWildcard(qt, query);
        }
        else {
            possibleCandidates = new ArrayList<>();
            for (QueryTerm singleQt : qt) {
                ArrayList<QueryTerm> temp = new ArrayList<>();
                temp.add(singleQt);
                possibleCandidates.add(temp);
            }
        }

        HashMap<Integer, PostingsEntry> docScore = new HashMap<>();
        
        for (ArrayList<QueryTerm> qtList : possibleCandidates) {
            for (QueryTerm term : qtList) {
                PostingsList pl = index.getPostings(term.term);
                if (pl == null) continue;
    
                for (int i = 0; i < pl.size(); i++) {
                    PostingsEntry pe = pl.get(i);
                    String[] name = index.docNames.get(pe.docID).split("\\\\");
                    if (docScore.containsKey(pe.docID)) {
                        docScore.get(pe.docID).score += index.docRanks.get(name[name.length - 1]);
                    }
                    else {
                        docScore.put(pe.docID, new PostingsEntry(pe.docID, index.docRanks.get(name[name.length - 1])));
                    }
                }
            }
        }

        ArrayList<PostingsEntry> answerPlList = new ArrayList<>(docScore.values());
        /* normalize */
        double sum = 0;
        for (PostingsEntry pe : answerPlList) {
            sum += pe.score;
        }
        for (PostingsEntry pe : answerPlList) {
            pe.score /= sum;
        }
        Collections.sort(answerPlList, new PostingsEntry.scoreComparator());
        
        return new PostingsList(answerPlList);
    }

    public PostingsList combinedSearch(ArrayList<QueryTerm> qt, NormalizationType normType, Query query) {
        HashMap<Integer, PostingsEntry> docScoreTFIDF = new HashMap<>();
        HashMap<Integer, PostingsEntry> docScorePR = new HashMap<>();
        final double TF_IDF_Weight = 0.8;

        List<ArrayList<QueryTerm>> possibleCandidates;
        boolean isWildcard = false;
        for (QueryTerm singleQt : qt) {
            if (this.kgIndex.isWildcard(singleQt.term)) {
                isWildcard = true;
                break;
            }
        }
        if (isWildcard) {
            possibleCandidates = kgIndex.parseWildcard(qt, query);
        }
        else {
            possibleCandidates = new ArrayList<>();
            for (QueryTerm singleQt : qt) {
                ArrayList<QueryTerm> temp = new ArrayList<>();
                temp.add(singleQt);
                possibleCandidates.add(temp);
            }
        }

        // List<ArrayList<QueryTerm>> possibleCandidates = kgIndex.parseWildcard(qt, query);

        for (ArrayList<QueryTerm> qtList : possibleCandidates) {
            for (QueryTerm term : qtList) {
                PostingsList pl = index.getPostings(term.term);
                if (pl == null) continue;
    
                for (int i = 0; i < pl.size(); i++) {
                    PostingsEntry pe = pl.get(i);
                    double tf_idf = tf_idf(pl, pe);
                    String[] name = index.docNames.get(pe.docID).split("\\\\");
                    
                    if (docScoreTFIDF.containsKey(pe.docID)) {
                        docScoreTFIDF.get(pe.docID).score += tf_idf;
                        docScorePR.get(pe.docID).score += index.docRanks.get(name[name.length - 1]);
                    }
                    else {
                        docScoreTFIDF.put(pe.docID, new PostingsEntry(pe.docID, tf_idf));
                        docScorePR.put(pe.docID, new PostingsEntry(pe.docID, index.docRanks.get(name[name.length - 1])));                
                    }
                }
            }
        }

        for (PostingsEntry pe : docScoreTFIDF.values()) {
            if (normType == NormalizationType.NUMBER_OF_WORDS) {
                pe.score /= index.docLengths.get(pe.docID);
            }
            else if (normType == NormalizationType.EUCLIDEAN) {
                pe.score /= index.l2Lengths.get(pe.docID);
            }
            else {
                throw new IllegalArgumentException("No such normalization type!");
            }
        }
        /* normalize */
        double sumTFIDF = 0;
        double sumPR = 0;
        for (int key : docScoreTFIDF.keySet()) {
            sumTFIDF += docScoreTFIDF.get(key).score;
            sumPR += docScorePR.get(key).score;
        }
        for (int key : docScoreTFIDF.keySet()) {
            docScoreTFIDF.get(key).score /= sumTFIDF;
            docScorePR.get(key).score /= sumPR;
            /* weighting */
            docScoreTFIDF.get(key).score = docScoreTFIDF.get(key).score * TF_IDF_Weight + docScorePR.get(key).score * (1 - TF_IDF_Weight);
        }

        ArrayList<PostingsEntry> answerPlList = new ArrayList<>(docScoreTFIDF.values());
        Collections.sort(answerPlList, new PostingsEntry.scoreComparator());
        
        return new PostingsList(answerPlList);
    }


    public double tf_idf(PostingsList pl, PostingsEntry pe) {
        return pe.offset.size() * Math.log(index.docNames.size() * 1.0 / pl.size());
    }

    
    private PostingsList intersect(ArrayList<QueryTerm> qt, QueryType queryType, Query query) {
        // List<ArrayList<QueryTerm>> possibleCandidates = kgIndex.parseWildcard(qt, query);

        List<ArrayList<QueryTerm>> possibleCandidates;
        boolean isWildcard = false;
        for (QueryTerm singleQt : qt) {
            if (this.kgIndex.isWildcard(singleQt.term)) {
                isWildcard = true;
                break;
            }
        }
        if (isWildcard) {
            possibleCandidates = kgIndex.parseWildcard(qt, query);
        }
        else {
            possibleCandidates = new ArrayList<>();
            for (QueryTerm singleQt : qt) {
                ArrayList<QueryTerm> temp = new ArrayList<>();
                temp.add(singleQt);
                possibleCandidates.add(temp);
            }
        }

        PostingsList result = new PostingsList();

        ArrayList<PostingsList> qtPostings = new ArrayList<PostingsList>();

        for (ArrayList<QueryTerm> qtList : possibleCandidates) {
            if (qtList.isEmpty()) return null;
            PostingsList postingsList = index.getPostings(qtList.get(0).term);
            for (int i = 1; i < qtList.size(); i++) {
                postingsList.mergePl(index.getPostings(qtList.get(i).term));
                if (postingsList == null) return null;
            }
            qtPostings.add(postingsList);
        }

        /* list of postingsList to be intersected: sort by increasing frequency in order to reduce time*/
        if (queryType == QueryType.INTERSECTION_QUERY) {
            PostingsList.sortByIncreasingFrequency(qtPostings);
        }
        result = qtPostings.get(0);

        int i = 1, qt_length = qt.size();
        while (i < qt_length && result != null) {
            /* INTERSECTION_QUERY */
            if (queryType == QueryType.INTERSECTION_QUERY) {
                result = positionlessIntersect(result, qtPostings.get(i));
            }
            /* PHRASE_QUERY */
            else if (queryType == QueryType.PHRASE_QUERY) {
                result = positionalIntersect(result, qtPostings.get(i));
            } else {
                throw new IllegalArgumentException("No such QueryType!");
            }
            i++;
        }

        return result;
    }

    @Deprecated
    private PostingsList mergeWildcardPostings(ArrayList<PostingsList> results) {
        PostingsList finalResult = new PostingsList();
        HashSet<Integer> check = new HashSet<>();
        for (PostingsList eachResult : results) {
            for (int i = 0; i < eachResult.size(); i++) {
                if (!check.contains(eachResult.get(i).docID)) {
                    check.add(eachResult.get(i).docID);
                    finalResult.appendEntry(new PostingsEntry(eachResult.get(i).docID));
                }
            }
        }
        return finalResult;
    }

    /* Intersection method used in INTERSECTION_QUERY */
    private PostingsList positionlessIntersect(PostingsList p1, PostingsList p2) {
        PostingsList answer = new PostingsList();
        int i1 = 0, i2 = 0;
        if (p1 == null || p2 == null) {
            return null;
        }
        int l1 = p1.size(), l2 = p2.size();
        while (i1 < l1 && i2 < l2) {
            int docID1 = p1.get(i1).docID, docID2 = p2.get(i2).docID;
            if (docID1 == docID2) {
                answer.addPostingsEntry(new PostingsEntry(docID1), -1); // -1 means we don't care offset in INTERSECTION_QUERY
                i1++;
                i2++;
            }
            else if (docID1 < docID2) {
                i1++;
            }
            else {
                i2++;
            }
        }
        return answer;
    }

    /* Intersection method used in PHRASE_QUERY */
    private PostingsList positionalIntersect(PostingsList p1, PostingsList p2) {
        PostingsList answer = new PostingsList();
        int i1 = 0, i2 = 0;
        if (p1 == null || p2 == null) {
            return null;
        }
        int l1 = p1.size(), l2 = p2.size();
        while (i1 < l1 && i2 < l2) {
            int docID1 = p1.get(i1).docID, docID2 = p2.get(i2).docID;
            if (docID1 == docID2) {
                ArrayList<Integer> reducedOffset = reduceOffsetMerge(p1.get(i1).offset, p2.get(i2).offset);
                if (!reducedOffset.isEmpty()) {
                    answer.addPostingsEntry(new PostingsEntry(docID1), reducedOffset);
                }
                i1++;
                i2++;
            }
            else if (docID1 < docID2) {
                i1++;
            }
            else {
                i2++;
            }
        }
        return answer;
    }

    private ArrayList<Integer> reduceOffsetMerge(ArrayList<Integer> offset1, ArrayList<Integer> offset2) {
        ArrayList<Integer> reducedOffset = new ArrayList<Integer>();
        int l1 = offset1.size(), l2 = offset2.size();
        int i1 = 0, i2 = 0;
        while (i1 < l1 && i2 < l2) {
            if (offset1.get(i1) < offset2.get(i2)) {
                if (offset1.get(i1) + 1 == offset2.get(i2)) {
                    reducedOffset.add(offset2.get(i2));
                    i1++;
                    i2++;
                }
                else {
                    i1++;
                }
            }
            else {
                i2++;
            }
        }
        return reducedOffset;
    }
}


