package edu.emory.mathcs.clir.relextract;

import edu.emory.mathcs.clir.relextract.data.Document;
import edu.emory.mathcs.clir.relextract.processor.Processor;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object of this class allows one to process a collection of documents with
 * some processor in parallel.
 */
public class ConcurrentProcessingRunner {

    /**
     * The name of the property to specify the number of threads to use. If the
     * property is missing all available threads will be used.
     */
    public static final String NUM_THREADS_PROPERTY =
            "ConcurrentProcessingRunner_numThreads";
    private final Processor processor_;
    private final int numThreads_;

    /**
     * Creates a new concurrent processing runner object, which encapsulates
     * a Processor and runs it in parallel over a collection of documents
     * provided through an iterator.
     *
     * @param processor  A processor to run over a collection of documents.
     * @param properties Some properties, e.g. it can specify the number of
     *                   threads to use.
     */
    public ConcurrentProcessingRunner(Processor processor,
                                      Properties properties) {
        processor_ = processor;
        if (!processor.isFrozen()) {
            processor.freeze();
        }
        numThreads_ = !properties.containsKey(NUM_THREADS_PROPERTY)
                ? Runtime.getRuntime().availableProcessors()
                : Integer.parseInt(
                properties.getProperty(NUM_THREADS_PROPERTY));
    }

    /**
     * Runs a processor over the provided collection of documents in parallel.
     * The number of threads to use can be specified in the Properties in the
     * constructor as the ConcurrentProcessingRunner.numThreads property.
     *
     * @param documents A collections of documents to process.
     * @throws InterruptedException
     */
    public void run(Iterable<Document.NlpDocument> documents)
            throws Exception {
        ExecutorService threadPool = Executors.newFixedThreadPool(numThreads_);
        final AtomicInteger counter = new AtomicInteger(0);
        final long startTime = System.currentTimeMillis();
        for (final Document.NlpDocument document : documents) {
            // Parser reads the next input on demand, that's why it cannot
            // predict that the input is over, so it will actually return null
            // element.
            if (document == null) continue;
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        processor_.process(document);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int processed = counter.incrementAndGet();
                    if (processed % 1000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        System.err.println("Processed: " + processed +
                                " (" + (1000.0 * processed /
                                (currentTime - startTime)) + " docs/sec)");
                    }
                }
            });
        }
        threadPool.shutdown();
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        processor_.finishProcessing();
    }
}
