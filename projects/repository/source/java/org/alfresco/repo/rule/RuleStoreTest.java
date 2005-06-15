/**
 * 
 */
package org.alfresco.repo.rule;

import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.rule.Rule;
import org.springframework.util.StopWatch;

/**
 * @author Roy Wetherall
 */
public class RuleStoreTest extends RuleBaseTest
{
    /**
     * Rule id
     */
    private static final String RULE_ID = "1";
    
    /**
     * Rule store
     */
    private RuleStore ruleStore;
    
    /**
     * @see org.springframework.test.AbstractTransactionalSpringContextTests#onSetUpInTransaction()
     */
    @Override
    protected void onSetUpInTransaction() throws Exception
    {
        super.onSetUpInTransaction();
        
        // Create the rule store
        this.ruleStore = new RuleStore(
                this.nodeService, 
                this.contentService,
                this.ruleConfig);
        
        // Make the test node actionable
        makeTestNodeActionable();
    }
    
    /**
     * Test get
     */
    public void testGet()
    {
        testPut();
        
        List<? extends Rule> rules = this.ruleStore.get(this.nodeRef, true);
        assertNotNull(rules);
        assertEquals(1, rules.size());
        
        RuleImpl rule = (RuleImpl)rules.get(0);
        checkRule(rule, RULE_ID);
    }
	
	/**
	 * Test getById
	 */
	public void testGetById()
	{
		Rule rule1 = this.ruleStore.getById(this.nodeRef, RULE_ID);
		assertNull(rule1);
		
		testPut();
		
		Rule rule2 = this.ruleStore.getById(this.nodeRef, RULE_ID);
		assertNotNull(rule2);
		assertEquals(RULE_ID, rule2.getId());
	}
    
    /**
     * Test put
     */
    public void testPut()
    {
        RuleImpl newRule = createTestRule(RULE_ID);
        this.ruleStore.put(this.nodeRef, newRule);
        
        NodeRef ruleContent = newRule.getRuleContentNodeRef();
        assertNotNull(ruleContent);
        
        ContentReader contentReader = this.contentService.getReader(ruleContent);
        assertNotNull(contentReader);
        String ruleXML = contentReader.getContentString();
        assertNotNull(ruleXML);
    }
    
    /**
     * Test getByRuleType
     */
    public void testGetByRuleType()
    {
        List<? extends Rule> empty = this.ruleStore.getByRuleType(this.nodeRef, RULE_TYPE);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
        
        testPut();
        List<? extends Rule> rules = this.ruleStore.getByRuleType(this.nodeRef, RULE_TYPE);
        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertEquals(RULE_TYPE_NAME, ((RuleImpl)rules.get(0)).getRuleType().getName());
        
        List<? extends Rule> empty2 = this.ruleStore.getByRuleType(this.nodeRef, new RuleTypeImpl("anOtherRuleType"));
        assertNotNull(empty2);
        assertTrue(empty2.isEmpty());        
    }
    
    /**
     * Test hasRules
     */
    public void testHasRules()
    {
        // Check that the node does not have any rules
        assertFalse(this.ruleStore.hasRules(this.nodeRef));
        
        // Put some rules and check that the value is now true
        testPut();
        assertTrue(this.ruleStore.hasRules(this.nodeRef));
    }
    
    /**
     * Test remove
     */
    public void testRemove()
    {
        RuleImpl newRule = createTestRule(RULE_ID);
        this.ruleStore.put(this.nodeRef, newRule);
        List<? extends Rule> rules = this.ruleStore.get(this.nodeRef, true);
        assertNotNull(rules);
        assertEquals(1, rules.size());
        
        this.ruleStore.remove(nodeRef, newRule);
        
        List<? extends Rule> moreRules = this.ruleStore.get(this.nodeRef, true);
        assertNotNull(moreRules);
        assertEquals(0, moreRules.size());        
    }
    
    /**
     * Test the performace of the cache with non hierarchical rules.
     */
    public void testCacheNonHierarchical()
    {
        StopWatch sw = new StopWatch();
        
        // Create actionable nodes
        sw.start("create actionable nodes");
        NodeRef[] nodes = new NodeRef[100];
        for (int i = 0; i < 100; i++)
        {
            NodeRef nodeRef = this.nodeService.createNode(
                    rootNodeRef,
					ContentModel.ASSOC_CONTAINS,
                    ContentModel.ASSOC_CONTAINS,
                    ContentModel.TYPE_CONTAINER).getChildRef();
            NodeRef configFolder = this.nodeService.createNode(
                    rootNodeRef,
					ContentModel.ASSOC_CONTAINS,
                    ContentModel.ASSOC_CONTAINS,
                    ContentModel.TYPE_CONFIGURATIONS).getChildRef();
            this.nodeService.addAspect(nodeRef, ContentModel.ASPECT_ACTIONABLE, null);
            this.nodeService.createAssociation(
                    nodeRef, 
                    configFolder, 
                    ContentModel.ASSOC_CONFIGURATIONS);
            nodes[i] = nodeRef;
        }
        sw.stop();
        
        sw.start("put 10 rules on each node");
        try
        {
            // Put rules
            for (int i = 0; i < 100; i++)
            {
                NodeRef nodeRef = nodes[i];
                for (int j = 0; j < 10; j++)
                {
                    RuleImpl newRule = createTestRule(Integer.toString(i) + "." + Integer.toString(j));
                    this.ruleStore.put(nodeRef, newRule);
                }
            }
        }
        finally
        {
            sw.stop();
        }
        
        sw.start("get rules (not cached)");
        try
        {
            // Get rules (not cached)
            for (int i = 0; i < 100; i++)
            {
                NodeRef nodeRef = nodes[i];
                List<RuleImpl> rules = (List<RuleImpl>)this.ruleStore.get(nodeRef, true);
                assertNotNull(rules);
                assertEquals(10, rules.size());
            }
        }
        finally
        {
            sw.stop();
        }
        
        sw.start("get rules (cached)");
        try
        {
            // Get rules (should now be cached)
            for (int i = 0; i < 100; i++)
            {
                NodeRef nodeRef = nodes[i];
                List<? extends Rule> rules = this.ruleStore.get(nodeRef, true);
                assertNotNull(rules);
                assertEquals(10, rules.size());
            }
        }
        finally
        {
            sw.stop();
        }
        
        sw.start("put & get rules (put one, get five)");
        try
        {
            // Put and get
            for (int i = 0; i < 95; i++)
            {
                NodeRef nodeRef = nodes[i];
                RuleImpl newRule = createTestRule(Integer.toString(i) + ".new");
                this.ruleStore.put(nodeRef, newRule);
                
                for (int j = 0; j < 5; j++)
                {
                    NodeRef getNodeRef = nodes[i+j];
                    List<? extends Rule> rules = this.ruleStore.get(getNodeRef, true);    
                    assertNotNull(rules);
                    if (j == 0)
                    {
                        assertEquals(11, rules.size());
                    }
                    else
                    {
                        assertEquals(10, rules.size());
                    }
                }
            }
        }
        finally
        {
            sw.stop();
        }
        
        
        System.out.println(sw.prettyPrint());
    }
}
