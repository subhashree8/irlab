package edu.emory.mathcs.ir.qa.answerer.index;

import edu.emory.mathcs.ir.input.YahooAnswersXmlInput;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a mediator for Lucene search index of QnA documents.
 */
public class QnAIndexDocument {
    public static final String ID_FIELD_NAME = "id";
    public static final String QTITLE_FIELD_NAME = "title";
    public static final String QBODY_FIELD_NAME = "body";
    public static final String QTITLEBODY_FIELD_NAME = "fullquestion";
    public static final String ANSWER_FIELD_NAME = "answer";
    public static final String MAIN_CATEGORY_FIELD_NAME = "maincategory";
    public static final String SUB_CATEGORY_FIELD_NAME = "subcategory";
    public static final String CATEGORY_FIELD_NAME = "category";

    private static final QueryBuilder queryBuilder_ =
            new QueryBuilder(new EnglishAnalyzer());

    /**
     * Creates Lucene IndexWriter for building QnA document index. The analyzers
     * for each field are specified.
     * @param indexLocation The directory of the index.
     * @return IndexWriter with per-field analyzers specified.
     * @throws IOException
     */
    public static IndexWriter createIndexWriter(Directory indexLocation)
            throws IOException {
        Map<String, Analyzer> analyzers = new HashMap<>();
        analyzers.put(QnAIndexDocument.ID_FIELD_NAME,
                new KeywordAnalyzer());
        analyzers.put(QnAIndexDocument.QTITLE_FIELD_NAME,
                new EnglishAnalyzer());
        analyzers.put(QnAIndexDocument.QBODY_FIELD_NAME, new EnglishAnalyzer());
        analyzers.put(QnAIndexDocument.QTITLEBODY_FIELD_NAME,
                new EnglishAnalyzer());
        analyzers.put(QnAIndexDocument.ANSWER_FIELD_NAME,
                new EnglishAnalyzer());
        analyzers.put(QnAIndexDocument.MAIN_CATEGORY_FIELD_NAME,
                new KeywordAnalyzer());
        analyzers.put(QnAIndexDocument.SUB_CATEGORY_FIELD_NAME,
                new KeywordAnalyzer());
        analyzers.put(QnAIndexDocument.CATEGORY_FIELD_NAME,
                new KeywordAnalyzer());
        final IndexWriterConfig config =
                new IndexWriterConfig(
                        new PerFieldAnalyzerWrapper(new SimpleAnalyzer(),
                                analyzers));
        return new IndexWriter(indexLocation, config);
    }

    /**
     * Converts QnA document to a Lucene Document object, which can be used for
     * indexing.
     * @param qna QnA pair to convert to Lucene document.
     * @return Lucene Document object with fields populated from the given QnA
     * pair.
     */
    public static Document getIndexDocument(YahooAnswersXmlInput.QnAPair qna) {
        final String titleAndBody = qna.questionTitle
                .concat("?\n").concat(qna.questionBody);
        final Document indexDocument = new Document();
        indexDocument.add(new StoredField(
                QnAIndexDocument.ID_FIELD_NAME, qna.id));
        indexDocument.add(new TextField(
                QnAIndexDocument.QTITLE_FIELD_NAME,
                qna.questionTitle, Field.Store.YES));
        indexDocument.add(new TextField(
                QnAIndexDocument.QBODY_FIELD_NAME, qna.questionBody,
                Field.Store.YES));
        indexDocument.add(new TextField(
                QnAIndexDocument.QTITLEBODY_FIELD_NAME,
                titleAndBody, Field.Store.YES));
        indexDocument.add(new TextField(
                QnAIndexDocument.ANSWER_FIELD_NAME, qna.bestAnswer,
                Field.Store.YES));
        indexDocument.add(new StringField(
                QnAIndexDocument.MAIN_CATEGORY_FIELD_NAME,
                qna.categories[0], Field.Store.YES));
        indexDocument.add(new StringField(
                QnAIndexDocument.SUB_CATEGORY_FIELD_NAME,
                qna.categories[1], Field.Store.YES));
        if (qna.categories.length > 2) {
            indexDocument.add(
                    new StringField(
                            QnAIndexDocument.CATEGORY_FIELD_NAME,
                            qna.categories[2], Field.Store.YES));
        }
        return indexDocument;
    }

    /**
     * Converts Lucene index document to QnA object.
     * @param indexDocument Lucene document to convert.
     * @return QnA pair object read from the Lucene index document.
     */
    public static YahooAnswersXmlInput.QnAPair getQnAPair(
            Document indexDocument) {
        final String category = indexDocument.get(CATEGORY_FIELD_NAME);
        final String[] categories = new String[category == null ? 2 : 3];
        categories[0] = indexDocument.get(MAIN_CATEGORY_FIELD_NAME);
        categories[1] = indexDocument.get(SUB_CATEGORY_FIELD_NAME);
        if (category != null) {
            categories[2] = category;
        }
        return new YahooAnswersXmlInput.QnAPair(
                indexDocument.get(ID_FIELD_NAME),
                indexDocument.get(QTITLE_FIELD_NAME),
                indexDocument.get(QBODY_FIELD_NAME),
                categories, indexDocument.get(ANSWER_FIELD_NAME), null);
    }

    /**
     * Retrives QnA pairs similar to the given document.
     * @param searcher Lucene index searcher.
     * @param qna Question-answer pair to search similar questions for.
     * @param topn The number of similar QnA documents to retrieve.
     * @return An array of QnAPair objects with similar questions and
     * corresponding answers.
     * @throws IOException
     */
    public static YahooAnswersXmlInput.QnAPair[] getSimilarQnAPairs(
            IndexSearcher searcher, YahooAnswersXmlInput.QnAPair qna, int topn)
            throws IOException {
        List<YahooAnswersXmlInput.QnAPair> results = new ArrayList<>();

        // Search for similar questions in the index.
        final Query q = queryBuilder_.createBooleanQuery(
                QnAIndexDocument.QTITLE_FIELD_NAME, qna.questionTitle);
        final TopDocs serp = searcher.search(q, topn);
        for (final ScoreDoc doc : serp.scoreDocs) {
            final int retrievedDocid = doc.doc;
            final YahooAnswersXmlInput.QnAPair retrieved =
                    QnAIndexDocument.getQnAPair(
                            searcher.getIndexReader().document(retrievedDocid));
            if (retrieved.id.equals(qna.id)) continue;
            results.add(retrieved);
        }
        return results.toArray(
                new YahooAnswersXmlInput.QnAPair[results.size()]);
    }

}