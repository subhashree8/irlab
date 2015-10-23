The proposed approach targets the problem of improving the performance of question answering systems using joint reasoning over unstructured, semi-structured and structured data sources.
By linking entity mentions to their knowledge base objects a text-based QA system will be able to use not only lexical information present in extracted text fragments, but also all the factual information about the entities, which should improve its performance.
On the other hand, knowledge base question answering should benefit from textual data about predicates and entities mentioned in a questions and a candidate answer.
Additional unstructured data will serve as a bridge between a natural language question and the corresponding knowledge base query, which should boost the recall of question answering systems.

However, there are certain questions and limitations, that I would like to discuss.
As we know, knowledge bases are inherently incomplete: not only many facts are missing, but also a set of predicates is far from being complete.
Therefore, for many questions there are no corresponding predicates in a knowledge base.
Given the fact that at the moment text-based QA systems outperform knowledge base systems on factoid questions from the TREC QA dataset, it is unclear how much additional information a KB can add and how big is an advantage over hybrid approaches that simply combine the candidates obtained from various data sources.
An alternative approach to get more knowledge about candidate answers is to retrieve more unstructured data, e.g. previous research found Wikipedia articles to be useful.
Another question is related to the usefulness of the information stored in a KB for complex and non-factoid questions.
The main challenge is to ``understand'' the text of the answer and  predict whether it replies to the question.
Facts stored in Freebase or similar KB might not reveal much about the meaning of the answer and we would need a different source of knowledge.


% Another issue relates to non-factoid questions.
% Knowledge bases contain factual information about entities and it's not clear how much we can benefit from this knowledge for non-factoid questions.
% Even though we can get some information about what are the different entities mentioned in an candidate answer paragraph and what are their relations, knowledge base won't help the system to understand the text.