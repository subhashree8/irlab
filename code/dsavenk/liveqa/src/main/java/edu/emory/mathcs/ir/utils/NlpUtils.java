package edu.emory.mathcs.ir.utils;

import edu.emory.mathcs.ir.qa.AppConfig;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides a set of natural language processing utils.
 */
public class NlpUtils {
    private static final AnnotationPipeline nlpPipeline_ =
            new StanfordCoreNLP(getProperties(), true);
    private static final Set<String> stopwords_ = new HashSet<>();

    // Static initializer.
    static {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        NlpUtils.class.getResourceAsStream(
                                "/stopwords_large.txt")));
        String word;
        try {
            while ((word = reader.readLine()) != null) {
                if (!word.isEmpty()) stopwords_.add(word);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the list of stopwords.
     *
     * @return A set of words that should be considered as stopwords.
     */
    public static Set<String> getStopwords() {
        return stopwords_;
    }

    private static Properties getProperties() {
        Properties properties = new Properties();
        properties.setProperty("annotators",
                AppConfig.PROPERTIES.getProperty("ANNOTATORS"));
        properties.setProperty("ssplit.newlineIsSentenceBreak", "always");
        properties.setProperty("parse.model",
                "edu/stanford/nlp/models/srparser/englishSR.ser.gz");
        return properties;
    }

    /**
     * Returns text annotated with Stanford CoreNLP pipeline.
     * @param text Text to annotate.
     * @return Text annotations.
     */
    public static Annotation getAnnotations(String text) {
        Annotation textAnnotation = new Annotation(text);
        nlpPipeline_.annotate(textAnnotation);
        return textAnnotation;
    }

    /**
     * Returns the list of NP chunks from the text.
     *
     * @param text Annotated text to extract chunks from.
     * @return List of chunks.
     */
    public static List<String> getChunks(Annotation text) {
        List<String> res = new ArrayList<>();
        text.get(CoreAnnotations.SentencesAnnotation.class).stream()
                .filter(sentence -> sentence.containsKey(
                        TreeCoreAnnotations.TreeAnnotation.class))
                .forEach(sentence -> extractChunks(sentence.get(
                        TreeCoreAnnotations.TreeAnnotation.class), res));
        return res;
    }

    private static void extractChunks(Tree sentenceTree, List<String> chunks) {
        for (Tree child : sentenceTree.children()) {
            if (child.isPhrasal() &&
                    (child.label().value().equals("NP") ||
                            child.label().value().equals("PRP") ||
                            child.label().value().equals("VP")) &&
                    Arrays.stream(child.children())
                            .noneMatch(Tree::isPhrasal)) {
                chunks.add(child.getLeaves().stream()
                        .flatMap(l -> l.yieldWords().stream().map(Word::word))
                        .collect(Collectors.joining(" ")));
            } else {
                extractChunks(child, chunks);
            }
        }
    }
}
