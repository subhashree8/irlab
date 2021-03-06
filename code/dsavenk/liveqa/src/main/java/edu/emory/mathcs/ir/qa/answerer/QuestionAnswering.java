package edu.emory.mathcs.ir.qa.answerer;

import edu.emory.mathcs.ir.qa.Answer;
import edu.emory.mathcs.ir.qa.Question;

/**
 * A general interface for question answering.
 */
public interface QuestionAnswering {
    /**
     * Returns the answer to the given question.
     * @param question The question to be answered.
     * @return The answer to the given question.
     */
    Answer GetAnswer(Question question);
}
