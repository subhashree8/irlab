package edu.emory.mathcs.clir.relextract.processor;

import edu.emory.mathcs.clir.relextract.data.Document;
import edu.emory.mathcs.clir.relextract.data.DocumentWrapper;
import edu.emory.mathcs.clir.relextract.extraction.Parameters;
import edu.emory.mathcs.clir.relextract.utils.DependencyTreeUtils;
import edu.emory.mathcs.clir.relextract.utils.KnowledgeBase;
import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Created by dsavenk on 3/20/15.
 */
public class QAModelTrainerProcessor extends Processor {

    private final Dataset<Boolean, Integer> dataset_ = new Dataset<>();
    private final KnowledgeBase kb_;
    private Random rnd_ = new Random(42);
    private String modelFile_;
    private LinearClassifier<Boolean, Integer> model_ = null;
    private String datasetFile_;
    private boolean split_ = false;

    private int alphabetSize_ = 10000000;
    private Map<String, Integer> alphabet_ = Collections.synchronizedMap(new HashMap<>());
    private Set<String> predicates_ = new HashSet<>();
    private double subsampleRate_ = 10;
    private boolean debug_ = false;
    private boolean useFineTypes_ = false;

    public static final String QA_MODEL_PARAMETER = "qa_model_path";
    public static final String QA_DATASET_PARAMETER = "qa_dataset_path";
    public static final String QA_PREDICATES_PARAMETER = "qa_predicates";
    public static final String QA_TEST_PARAMETER = "qa_test";
    public static final String QA_SUBSAMPLE_PARAMETER = "qa_subsample";
    public static final String SPLIT_DATASET_PARAMETER = "qa_split_data";
    public static final String QA_USE_FREEBASE_TYPESFEATURES = "qa_finetypes_features";
    public static final String QA_DEBUG_PARAMETER = "qa_debug";

    BufferedWriter out;

    /**
     * Processors can take parameters, that are stored inside the properties
     * argument.
     *
     * @param properties A set of properties, the processor doesn't have to
     *                   consume all of the them, it checks what it needs.
     */
    public QAModelTrainerProcessor(Properties properties) {
        super(properties);
        kb_ = KnowledgeBase.getInstance(properties);
        modelFile_ = properties.getProperty(QA_MODEL_PARAMETER);
        if (properties.containsKey(QA_TEST_PARAMETER)) {
            model_ = LinearClassifier.readClassifier(modelFile_);
        }
        if (properties.containsKey(SPLIT_DATASET_PARAMETER)) {
            split_ = true;
        }
        if (properties.containsKey(QA_SUBSAMPLE_PARAMETER)) {
            subsampleRate_ = Double.parseDouble(properties.getProperty(QA_SUBSAMPLE_PARAMETER));
        }
        if (properties.containsKey(QA_DEBUG_PARAMETER)) {
            debug_ = true;
        }
        if (properties.containsKey(QA_USE_FREEBASE_TYPESFEATURES)) {
            useFineTypes_ = true;
        }
        datasetFile_ = properties.getProperty(QA_DATASET_PARAMETER);
        if (properties.containsKey(QA_PREDICATES_PARAMETER)) {
            try {
                BufferedReader input = new BufferedReader(new FileReader(properties.getProperty(QA_PREDICATES_PARAMETER)));
                String line;
                while ((line = input.readLine()) != null) {
                    String[] countPred = line.trim().split(" ");
                    if (Integer.parseInt(countPred[0]) > 100 && !(countPred[1].startsWith("base.") || countPred[1].startsWith("user."))) {
                        predicates_.add(countPred[1]);
                    }
                }
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        out = new BufferedWriter(new OutputStreamWriter(System.out));
    }

    @Override
    protected Document.NlpDocument doProcess(Document.NlpDocument document) throws Exception {
        boolean isTraining = model_ == null;

        if (isTraining) {
            if (document.getQaInstanceCount() == 0
                    || document.getQaInstanceList().stream().filter(x -> predicates_.isEmpty() || predicates_.contains(x.getPredicate())).noneMatch(Document.QaRelationInstance::getIsPositive)) {
                return null;
            }
        }

        boolean isInTraining = ((document.getText().hashCode() & 0x7FFFFFFF) % 10) < 7;
        if (split_ && (isInTraining != isTraining)) return null;

        DocumentWrapper documentWrapper = new DocumentWrapper(document);

//        int[] tokenMentions = new int[documentWrapper.getQuestionSentenceCount() < document.getSentenceCount()
//                ? document.getSentence(documentWrapper.getQuestionSentenceCount()).getFirstToken()
//                : document.getTokenCount()];
//        Arrays.fill(tokenMentions, -1);

        Set<String> qDepPaths = new HashSet<>();

        for (Document.Span span : document.getSpanList()) {
            if (span.hasEntityId() && span.getCandidateEntityScore(0) > Parameters.MIN_ENTITYID_SCORE) {
                for (Document.Mention mention : span.getMentionList()) {
                    if (mention.getSentenceIndex() < documentWrapper.getQuestionSentenceCount()) {
                        String path = DependencyTreeUtils.getQuestionDependencyPath(document,
                                mention.getSentenceIndex(),
                                DependencyTreeUtils.getMentionHeadToken(document, mention));
                        if (path != null)
                            qDepPaths.add(path);
                    }
                }
            }
        }

        List<String> questionFeatures = new ArrayList<>();
        for (int sentence = 0; sentence < document.getSentenceCount() && sentence < documentWrapper.getQuestionSentenceCount(); ++sentence) {
            questionFeatures.addAll(new QuestionGraph(documentWrapper, kb_, sentence, useFineTypes_).getEdgeFeatures());
        }

        questionFeatures.addAll(qDepPaths);

        PriorityQueue<Triple<Double, Document.QaRelationInstance, String>> scores = new PriorityQueue<>((o1, o2) -> o2.first.compareTo(o1.first));
        StringWriter strWriter = debug_ ? new StringWriter() : null;
        PrintWriter debugWriter = debug_ ? new PrintWriter(strWriter) : null;
        for (Document.QaRelationInstance instance : document.getQaInstanceList()) {
            if (!predicates_.isEmpty() && !predicates_.contains(instance.getPredicate())) {
                continue;
            }

            // Ignore self-triples
            if (kb_.convertFreebaseMidRdf(instance.getObject()).equals(kb_.convertFreebaseMidRdf(instance.getSubject())))
                continue;

            if (isTraining) {
                if (!instance.getIsPositive()) {
                    if (rnd_.nextInt(1000) > subsampleRate_) continue;
                }
            }

//            for (String str : features) {
//                alphabet_.put(str, (str.hashCode() & 0x7FFFFFFF) % alphabetSize_);
//            }
            List<String> answerFeatures = generateAnswerFeatures(instance);
            //generateFeatures(documentWrapper, instance, qDepPaths, features);
//            synchronized (out) {
//                out.write(instance.getIsPositive() ? "1" : "-1" + " |");
//                for (String feat : features) {
//                    out.write(" " + feat.replace(" ", "_").replace("\t", "_").replace("\n", "_").replace("|", "/"));
//                }
//                out.write("\n");
//            }

            Set<Integer> feats = new HashSet<>();
            for (String aFeature : answerFeatures) {
                for (String qFeature : questionFeatures) {
                    String feature = qFeature + "||" + aFeature;
                    int id = (feature.hashCode() & 0x7FFFFFFF) % alphabetSize_;
                    if (debug_) {
                        debugWriter.println(id + "\t" + feature);
                    }
                    feats.add(id);
                }
            }

            if (isTraining) {
                synchronized (dataset_) {
                    //System.out.println(instance.getIsPositive() + "\t" + features.stream().collect(Collectors.joining("\t")));
                    dataset_.add(feats, instance.getIsPositive());
                    //dataset_.add(features, instance.getIsPositive());
                }
            } else {
                Triple<Double, Document.QaRelationInstance, String> tr =
                        new Triple<>(model_.probabilityOf(new BasicDatum<>(feats)).getCount(true), instance, "");
                if (debug_) {
                    model_.justificationOf(new BasicDatum<>(feats), debugWriter);
                    debugWriter.flush();
                    tr.third = strWriter.toString();
                    strWriter.getBuffer().setLength(0);
                }
                scores.add(tr);
            }
        }

        if (!isTraining) {
            String[] fields = document.getText().split("\n");
            String utterance = fields[0];
            StringBuilder answers = new StringBuilder();
            answers.append("[");
            for (int i = 1; i < fields.length; ++i) {
                answers.append(fields[i].replace(",", ""));
                if (i < fields.length - 1) answers.append(",");
            }
            answers.append("]");

            StringBuilder prediction = new StringBuilder();
            prediction.append("[");
            Set<String> predictionsSet = new HashSet<>();

            StringBuilder debugInfo = debug_ ? new StringBuilder() : null;
            if (!scores.isEmpty()) {
                double bestScore = scores.peek().first;
                String bestSubject = scores.peek().second.getSubject();
                String bestPredicate = scores.peek().second.getPredicate();
                boolean first = true;
                while (bestScore > 0.5 && !scores.isEmpty()
                        && scores.peek().first == bestScore && scores.peek().second.getSubject().equals(bestSubject)
                        && scores.peek().second.getPredicate().equals(bestPredicate)) {
                    Triple<Double, Document.QaRelationInstance, String> tr = scores.poll();
                    Document.QaRelationInstance e = tr.second;
                    if (!first) prediction.append(",");
                    prediction.append("\"");
                    String value;
                    if (e.getObject().startsWith("http://")) {
                        value = kb_.getEntityName(e.getObject());
                    } else {
                        value = e.getObject();
                        if (e.getObject().contains("^^")) {
                            value = e.getObject().split("\\^\\^")[0].replace("\"", "");
                            // Reformat dates
                            if (value.contains("-")) {
                                String[] parts = value.split("\\-");
                                if (parts.length == 3)
                                    value = String.format("%s/%s/%s", parts[1], parts[2], parts[0]);
                                else if (parts.length == 2)
                                    value = String.format("%s/%s", parts[1], parts[0]);
                            }
                        }
                    }
                    if (value.contains("\"")) {
                        value = value.replace("\"", "\\\"");
                    }
                    if (!predictionsSet.contains(value)) {
                        prediction.append(value);
                        predictionsSet.add(value);
                    }
                    prediction.append("\"");
                    first = false;

                    if (debug_) {
                        debugInfo.append("-------\n");
                        debugInfo.append(e.getSubject() + "\t" + e.getPredicate() + "\t" + e.getObject() + "\n");
                        debugInfo.append(tr.third);
                        debugInfo.append("\n\n");
                    }
                }
            }
            prediction.append("]");
            synchronized (this) {
                System.out.println(String.format("%s\t%s\t%s", utterance, answers, prediction));
                if (debug_) {
                    System.out.println(debugInfo);
                }
            }
        }

        return document;
    }

    private List<String> generateAnswerFeatures(Document.QaRelationInstance instance) {
        return Arrays.asList(instance.getPredicate());
    }

    @Override
    public void finishProcessing() {
        if (model_ == null) {
            dataset_.summaryStatistics();

            // TODO(dsavenk): Comment this out for now.
//            try {
//                PrintWriter out = new PrintWriter(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(datasetFile_))));
//                for (Datum<Boolean, Integer> d : dataset_) {
//                    out.println(d.label() ? "1" : "-1" + " | " + d.asFeatures().stream().sorted().map(Object::toString).collect(Collectors.joining(" ")));
//                }
//                out.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            LinearClassifierFactory<Boolean, Integer> classifierFactory_ =
                    new LinearClassifierFactory<>(1e-4, false, 1.0);
            //classifierFactory_.setTuneSigmaHeldOut();
            classifierFactory_.useInPlaceStochasticGradientDescent();

//        classifierFactory_.setMinimizerCreator(() -> {
//            QNMinimizer min = new QNMinimizer(15);
//            min.useOWLQN(true, 1.0);
//            return min;
//        });
            classifierFactory_.setVerbose(true);
            model_ = classifierFactory_.trainClassifier(dataset_);
            LinearClassifier.writeClassifier(model_, modelFile_);
        }
    }

    private String getFeature(String[] parts, String... valParts) {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < parts.length; ++i) {
            res.append(parts[i]);
            res.append(":");
            res.append(valParts[i]);
            res.append("|");
        }
        return res.toString();
    }


    static class QuestionGraph {
        enum NodeType {
            REGULAR,
            QWORD,
            QFOCUS,
            QTOPIC,
            QVERB,
            PREPOSITION
        }

        static class Node {
            public List<Pair<Integer, String>> parent = new ArrayList<>();
            public NodeType type = NodeType.REGULAR;
            public List<String> values = new ArrayList<>();

            public String[] getValues() {
                String[] res = new String[values.size()];
                int i = 0;
                for (String value : values) {
                    res[i++] = type != NodeType.REGULAR && type != NodeType.PREPOSITION ? type + "=" + value : value;
                }
                return res;
            }

            @Override
            public String toString() {
                return type != NodeType.REGULAR && type != NodeType.PREPOSITION ? type + "=" + values : values.toString();
            }
        }

        private List<Node> nodes_ = new ArrayList<>();

        QuestionGraph(DocumentWrapper document, KnowledgeBase kb, int sentence, boolean useFreebaseTypes) {
            int firstToken = document.document().getSentence(sentence).getFirstToken();
            int lastToken = document.document().getSentence(sentence).getLastToken();
            int[] tokenToNodeIndex = new int[lastToken - firstToken];
            Arrays.fill(tokenToNodeIndex, -1);
            for (int token = firstToken; token < lastToken; ++token) {
                int mentionHead = document.getTokenMentionHead(token);
                Node node = null;
                if (Character.isAlphabetic(document.document().getToken(token).getPos().charAt(0))) {
                    if (mentionHead != -1) {
                        if (mentionHead == token) {
                            node = new Node();
                            boolean measure = document.isTokenMeasure(token);
                            node.type = measure ? NodeType.REGULAR : NodeType.QTOPIC;
                            if (!measure && useFreebaseTypes) {
                                for (Document.Span span : document.getTokenSpan(token)) {
                                    for (int i = 0; i < span.getCandidateEntityIdCount() && span.getCandidateEntityScore(i) >= Parameters.MIN_ENTITYID_SCORE; ++i) {
                                        node.values.addAll(kb.getEntityTypes(span.getCandidateEntityId(i), false).stream().filter(x -> !x.startsWith("common.") && !x.startsWith("base.") && !x.startsWith("user.")).collect(Collectors.toList()));
                                    }
                                }
                            } else {
                                node.values = document.document().getToken(token).getNer().equals("O")
                                        ? Arrays.asList(document.document().getToken(token).getLemma())
                                        : Arrays.asList(document.document().getToken(token).getNer());
                            }
                        }
                    } else {
                        if (!document.document().getToken(token).getPos().startsWith("D")
                                && !document.document().getToken(token).getPos().startsWith("PD")) {
                            node = new Node();
                            node.values = Arrays.asList(document.document().getToken(token).getLemma());
                            if (document.document().getToken(token).getPos().startsWith("W")) {
                                node.type = NodeType.QWORD;
                            } else if (document.document().getToken(token).getPos().startsWith("V") || document.document().getToken(token).getPos().startsWith("MD")) {
                                node.type = NodeType.QVERB;
                            } else {
                                if (document.document().getToken(token).getPos().startsWith("IN")) {
                                    node.type = NodeType.PREPOSITION;
                                } else {
                                    node.type = NodeType.REGULAR;
                                }
                            }
                        }
                    }
                }
                if (node != null) {
                    tokenToNodeIndex[token - firstToken] = nodes_.size();
                    nodes_.add(node);
                }
            }

            for (int token = firstToken; token < lastToken; ++token) {
                if (tokenToNodeIndex[token] != -1) {
                    Node node = nodes_.get(tokenToNodeIndex[token]);
                    int parent = document.document().getToken(token).getDependencyGovernor();
                    if (parent != 0) {
                        --parent;
                        if (document.document().getToken(firstToken + parent).getPos().startsWith("W")) {
                            node.type = NodeType.QFOCUS;
                        }
                        if (tokenToNodeIndex[parent] != -1) {
                            node.parent.add(new Pair<>(tokenToNodeIndex[parent], document.document().getToken(token).getDependencyType()));
                        }
                    }
                }
            }
        }

        List<String> getEdgeFeatures() {
            List<String> res = new ArrayList<>();
            for (Node node : nodes_) {
                if (node != null) {
                    if (node.type != NodeType.PREPOSITION) {
                        for (String feat : node.getValues()) {
                            res.add(feat);
                        }
                    }
                    for (Pair<Integer, String> parent : node.parent) {
                        for (String nodeStr : node.getValues()) {
                            for (String parentNodeStr : nodes_.get(parent.first).getValues()) {
                                res.add(nodeStr + "->" + parentNodeStr);
                                res.add(nodeStr + "-" + parent.second + "->" + parentNodeStr);
                            }
                        }
                    }
                }
            }
            return res;
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder();
            for (Node node : nodes_) {
                if (node != null) {
                    res.append(node.type);
                    res.append("\t");
                    res.append(node.values.toString());
                    res.append("\n");
                }
            }
            return res.toString();
        }
    }
}

