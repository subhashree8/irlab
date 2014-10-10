package edu.emory.mathcs.clir.relextract.utils;

import java.util.Properties;

public class KnowledgeBaseTest {

    private KnowledgeBase kb_;

    @org.junit.Before
    public void setUp() throws Exception {
        // TODO(denxx): Create small model and put it to the resources.
        Properties props = new Properties();
        props.setProperty("kb", "/home/dsavenk/Projects/octiron/data/Freebase/" +
                "jena_model/");
        kb_ = KnowledgeBase.getInstance(props);
    }

//    @org.junit.Test
//    public void testGetSubjectTriples() throws Exception {
//        StmtIterator iter = kb_.getSubjectPredicateTriples("/m/053w4",
//                "people.person.date_of_birth");
//        assertTrue(iter.hasNext());
//        while (iter.hasNext()) {
//            Statement st = iter.nextStatement();
//            System.out.println(st.getObject().toString());
//            System.out.println(st.getObject().isLiteral());
//            System.out.println(st.getObject().asLiteral().getDatatype());
//            System.out.println(st.getObject().asLiteral().getString());
//        }
//    }
//
//    @org.junit.Test
//    public void testGetSubjectTriples2() throws Exception {
//        StmtIterator iter = kb_.getSubjectMeasureTriples("/m/02mjmr",
//                "44", XSDDatatype.XSDinteger);
//        Assert.assertTrue(iter.hasNext());
//        while (iter.hasNext()) {
//            Statement st = iter.nextStatement();
//            System.out.println(st.getPredicate().toString());
//        }
//    }
//
//    @Test
//    public void testSPARQL() throws Exception {
//        Set<KnowledgeBase.Triple> res =
//                kb_.getSubjectObjectTriplesCVTSparql("/m/02mjmr", "/m/025s5v9");
//        Assert.assertTrue(!res.isEmpty());
//        for (KnowledgeBase.Triple triple : res) {
//            System.out.println(triple.predicate);
//        }
//    }
//
//
//    @org.junit.Test
//    public void testGetSubjectObjectTriples() throws Exception {
//
//    }

//    @org.junit.Test
//    public void testGetSubjectTriples2() throws Exception {
//        Set<KnowledgeBase.Triple> triples =
//                kb_.getSubjectObjectTriplesCVT("/m/09b6zr", "/m/04g8d");
//        Assert.assertTrue(triples.isEmpty());
//    }

}