/**
 * 
 */
package org.alfresco.repo.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.config.ConfigService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.rule.Rule;
import org.alfresco.service.cmr.rule.RuleAction;
import org.alfresco.service.cmr.rule.RuleActionDefinition;
import org.alfresco.service.cmr.rule.RuleCondition;
import org.alfresco.service.cmr.rule.RuleConditionDefinition;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.rule.RuleServiceException;
import org.alfresco.service.cmr.rule.RuleType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;

/**
 * 
 * @author Roy Wetherall   
 */
public class RuleServiceImpl implements RuleService
{
    /**
     * The config service
     */
    private ConfigService configService;
    
    /**
     * The node service
     */
    private NodeService nodeService;
    
    /**
     * The content service
     */
    private ContentService contentService;
    
    /**
     * The dictionary service
     */
    private DictionaryService dictionaryService;
	
	/**
	 * The service registry
	 */
	private ServiceRegistry serviceRegistry;
    
    /**
     * The policy component
     */
    private PolicyComponent policyComponent;
    
    /**
     * The rule config
     */
    private RuleConfig ruleConfiguration;
    
    /**
     * The rule store
     */
    private RuleStore ruleStore;
    
    /**
     * List of rule type adapters
     */
    private List<RuleTypeAdapter> adapters;
	
	/**
	 * Thread local set containing all the pending rules to be executed when the transaction ends
	 */
	private ThreadLocal<Set<PendingRuleData>> threadLocalPendingData = new ThreadLocal<Set<PendingRuleData>>();
	
	/**
	 * Thread local set to the currently executing rule (this should prevent and execution loops)
	 */
	// TODO whta about more complex scenarios when loop can be created?
	private ThreadLocal<Rule> threadLocalCurrenltyExecutingRule = new ThreadLocal<Rule>();
    
    /**
     * Service intialization method
     */
    public void init()
    {
        // Create the rule configuration and store
        this.ruleConfiguration = new RuleConfig(this.configService);
        this.ruleStore = new RuleStore(
                this.nodeService, 
                this.contentService,
                this.ruleConfiguration);
        
        // Initialise the rule types
        initRuleTypes();
    }

    /**
     * Initialise the rule types
     */
    private void initRuleTypes()
    {
        // Register the rule type policy bahviours
        List<RuleType> ruleTypes = getRuleTypes();
        List<RuleTypeAdapter> adapters = new ArrayList<RuleTypeAdapter>(ruleTypes.size());
        for (RuleType ruleType : ruleTypes)
        {
            // Create the rule type adapter and register policy bahaviours
            String ruleTypeAdapter = ((RuleTypeImpl)ruleType).getRuleTypeAdapter();
            if (ruleTypeAdapter != null && ruleTypeAdapter.length() != 0)
            {
                try 
                {
                    // Create the rule type adapter
                    RuleTypeAdapter adapter = (RuleTypeAdapter)Class.forName(ruleTypeAdapter).
                            getConstructor(new Class[]{RuleType.class, RuleService.class, PolicyComponent.class, ServiceRegistry.class}).
                            newInstance(new Object[]{ruleType, this, this.policyComponent, this.serviceRegistry});
                    
                    // Register the adapters policy behaviour
                    adapter.registerPolicyBehaviour();
                }
                catch(Exception exception)
                {
                    // Error creating and initialising the adapter
                    throw new RuleServiceException("Unable to initialise the rule type adapter.", exception);
                }
            }
        }
    }       

    /**
     * Set the config service
     * 
     * @param configService     the config service
     */
    public void setConfigService(ConfigService configService)
    {
        this.configService = configService;
    }
    
    /**
     * Set the node service 
     * 
     * @param nodeService   the node service
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }
    
    /**
     * Set the content service
     * 
     * @param contentService    the content service
     */
    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService;
    }
    
    /**
     * Set the dictionary service
     * 
     * @param dictionaryService     the dictionary service
     */
    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }
	
	/**
	 * Set the service registry
	 * 
	 * @param serviceRegistry	the service registry
	 */
	public void setServiceRegistry(ServiceRegistry serviceRegistry) 
	{
		this.serviceRegistry = serviceRegistry;
	}
    
    /**
     * Sets the policy component
     * 
     * @param policyComponent  the policy component
     */
    public void setPolicyComponent(PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }
    
    /**
     * @see org.alfresco.repo.rule.RuleService#getRuleTypes()
     */
    public List<RuleType> getRuleTypes()
    {
        // Get the rule types from the rule config
        Collection<RuleTypeImpl> ruleTypes = this.ruleConfiguration.getRuleTypes();
        ArrayList<RuleType> result = new ArrayList<RuleType>(ruleTypes.size());
        result.addAll(ruleTypes);
        return result;
    }
    
    /**
     * @see org.alfresco.repo.rule.RuleService#getRuleType(java.lang.String)
     */
    public RuleType getRuleType(String name)
    {
        return this.ruleConfiguration.getRuleType(name);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#getConditionDefinitions()
     */
    public List<RuleConditionDefinition> getConditionDefinitions()
    {
        // Get the condition defintion from the rule config
        Collection<RuleConditionDefinitionImpl> items = this.ruleConfiguration.getConditionDefinitions();
        ArrayList<RuleConditionDefinition> result = new ArrayList<RuleConditionDefinition>(items.size());
        result.addAll(items);
        return result;
    }
    
    /**
     * @see org.alfresco.repo.rule.RuleService#getConditionDefintion(java.lang.String)
     */
    public RuleConditionDefinition getConditionDefintion(String name)
    {
        return this.ruleConfiguration.getConditionDefinition(name);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#getActionDefinitions()
     */
    public List<RuleActionDefinition> getActionDefinitions()
    {
        // Get the rule action defintions from the config
        Collection<RuleActionDefinitionImpl> items = this.ruleConfiguration.getActionDefinitions();
        ArrayList<RuleActionDefinition> result = new ArrayList<RuleActionDefinition>(items.size());
        result.addAll(items);
        return result;
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#getActionDefinition(java.lang.String)
     */
    public RuleActionDefinition getActionDefinition(String name)
    {
        return this.ruleConfiguration.getActionDefinition(name);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#makeActionable(org.alfresco.repo.ref.NodeRef, org.alfresco.repo.ref.NodeRef)
     */
    public void makeActionable(
            NodeRef nodeRef)
    {
        // Get the root config node
		NodeRef rootConfigFolder = getRootConfigNodeRef(nodeRef);
		
		// Create the configuraion folder
		NodeRef configurationsNodeRef = this.nodeService.createNode(
											rootConfigFolder,
											ContentModel.ASSOC_CONTAINS,
											QName.createQName(NamespaceService.ALFRESCO_URI, "configurations"),
											ContentModel.TYPE_CONFIGURATIONS).getChildRef();
		
        // Apply the aspect and add the configurations folder
        this.nodeService.addAspect(
                nodeRef, 
                ContentModel.ASPECT_ACTIONABLE, 
                null);
        this.nodeService.createAssociation(
                nodeRef, 
                configurationsNodeRef, 
                ContentModel.ASSOC_CONFIGURATIONS);	
    }

	/**
	 * Get the root config node reference
	 * 
	 * @param nodeRef	the node reference
	 * @return			the root config node reference
	 */
	private NodeRef getRootConfigNodeRef(NodeRef nodeRef) 
	{
		// TODO maybe this should be cached ...
		// TODO the QNames should be put in the DicitionaryBootstrap
		
		NodeRef rootConfigFolder = null;
		NodeRef rootNode = this.nodeService.getRootNode(nodeRef.getStoreRef());
		List<ChildAssociationRef> childAssocRefs = this.nodeService.getChildAssocs(
							  					rootNode, 
												QName.createQName(NamespaceService.ALFRESCO_URI, "systemconfiguration"));
		if (childAssocRefs.size() == 0)
		{
			rootConfigFolder = this.nodeService.createNode(
												rootNode,
												ContentModel.ASSOC_CHILDREN,
												QName.createQName(NamespaceService.ALFRESCO_URI, "systemconfiguration"),
												ContentModel.TYPE_SYTEM_FOLDER).getChildRef();
		}
		else
		{
			rootConfigFolder = childAssocRefs.get(0).getChildRef();
		}
		return rootConfigFolder;
	}

    /**
     * @see org.alfresco.repo.rule.RuleService#isActionable(org.alfresco.repo.ref.NodeRef)
     */
    public boolean isActionable(NodeRef nodeRef)
    {
        // Determine whether a node is actionable or not
        return (this.nodeService.hasAspect(nodeRef, ContentModel.ASPECT_ACTIONABLE) == true);          
    }
    
    /**
     * @see org.alfresco.repo.rule.RuleService#hasRules(org.alfresco.repo.ref.NodeRef)
     */
    public boolean hasRules(NodeRef nodeRef)
    {
        return this.ruleStore.hasRules(nodeRef);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#getRules(org.alfresco.repo.ref.NodeRef)
     */
    public List<Rule> getRules(NodeRef nodeRef)
    {
        return getRules(nodeRef, true);
    }

    /**  
     * @see org.alfresco.repo.rule.RuleService#getRules(org.alfresco.repo.ref.NodeRef, boolean)
     */
    public List<Rule> getRules(NodeRef nodeRef, boolean includeInhertied)
    {
        return (List<Rule>)this.ruleStore.get(nodeRef, includeInhertied);
    }
	
	/**
	 * @see org.alfresco.repo.rule.RuleService#getRule(String) 
	 */
	public Rule getRule(NodeRef nodeRef, String ruleId) 
	{
		return this.ruleStore.getById(nodeRef, ruleId);
	}

    /**
     * @see org.alfresco.repo.rule.RuleService#getRulesByRuleType(org.alfresco.repo.ref.NodeRef, org.alfresco.repo.rule.RuleType)
     */
    public List<Rule> getRulesByRuleType(NodeRef nodeRef, RuleType ruleType)
    {
        return (List<Rule>)this.ruleStore.getByRuleType(nodeRef, ruleType);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#createRule(org.alfresco.repo.rule.RuleType)
     */
    public Rule createRule(RuleType ruleType)
    {
        // Create the new rule, giving it a unique rule id
        String id = GUID.generate();
        return new RuleImpl(id, ruleType);
    }

    /**
     * @see org.alfresco.repo.rule.RuleService#addRule(org.alfresco.repo.ref.NodeRef, org.alfresco.repo.rule.Rule)
     */
    public void addRule(NodeRef nodeRef, Rule rule)
    {
        // Add the rule to the rule store
        this.ruleStore.put(nodeRef, (RuleImpl)rule);
    }
    
    /**
     * @see org.alfresco.repo.rule.RuleService#removeRule(org.alfresco.repo.ref.NodeRef, org.alfresco.repo.rule.RuleImpl)
     */
    public void removeRule(NodeRef nodeRef, Rule rule)
    {
        // Remove the rule from the rule store
        this.ruleStore.remove(nodeRef, (RuleImpl)rule);
    }	
	
	/**
	 * @see org.alfresco.repo.rule.RuleService#addRulePendingExecution(NodeRef, NodeRef, Rule)
	 */
	public void addRulePendingExecution(NodeRef actionableNodeRef, NodeRef actionedUponNodeRef, Rule rule) 
	{
		PendingRuleData pendingRuleData = new PendingRuleData(actionableNodeRef, actionedUponNodeRef, rule);
		Rule currentlyExecutingRule = this.threadLocalCurrenltyExecutingRule.get();
		
		if (currentlyExecutingRule == null || currentlyExecutingRule.equals(rule) == false)
		{
			Set<PendingRuleData> pendingRules = this.threadLocalPendingData.get();
			if (pendingRules == null)
			{
				pendingRules = new HashSet<PendingRuleData>();					
			}
			
			pendingRules.add(pendingRuleData);		
			this.threadLocalPendingData.set(pendingRules);
		}
	}

	/**
	 * @see org.alfresco.repo.rule.RuleService#executePendingRules()
	 */
	public void executePendingRules() 
	{
		Set<PendingRuleData> pendingRules = this.threadLocalPendingData.get();
		if (pendingRules != null)
		{
			PendingRuleData[] pendingRulesArr = pendingRules.toArray(new PendingRuleData[0]);
			this.threadLocalPendingData.remove();
			for (PendingRuleData pendingRule : pendingRulesArr) 
			{
				threadLocalCurrenltyExecutingRule.set(pendingRule.getRule());
				try
				{
					executePendingRule(pendingRule);
				}
				finally
				{
					threadLocalCurrenltyExecutingRule.remove();
				}
			}
			
			// Run any rules that have been marked as pending during execution
			executePendingRules();
		}		
	}     
	
	/**
	 * Executes a pending rule
	 * 
	 * @param pendingRule	the pending rule data object
	 */
	private void executePendingRule(PendingRuleData pendingRule) 
	{
		NodeRef actionableNodeRef = pendingRule.getActionableNodeRef();
		NodeRef actionedUponNodeRef = pendingRule.getActionedUponNodeRef();
		Rule rule = pendingRule.getRule();
		
		// Get the rule conditions
		List<RuleCondition> conds = rule.getRuleConditions();				
		if (conds.size() == 0)
		{
			throw new RuleServiceException("No rule conditions have been specified for the rule.");
		}
		else if (conds.size() > 1)
		{
			// TODO at the moment we only support one rule condition
			throw new RuleServiceException("Currently only one rule condition can be specified per rule.");
		}
		
		// Get the single rule condition
		RuleCondition cond = conds.get(0);
	    RuleConditionEvaluator evaluator = getConditionEvaluator(cond);
	      
	    // Get the rule acitons
	    List<RuleAction> actions = rule.getRuleActions();
		if (actions.size() == 0)
		{
			throw new RuleServiceException("No rule actions have been specified for the rule.");
		}
		else if (actions.size() > 1)
		{
			// TODO at the moment we only support one rule action
			throw new RuleServiceException("Currently only one rule action can be specified per rule.");
		}
			
		// Get the single action
	    RuleAction action = actions.get(0);
	    RuleActionExecuter executor = getActionExecutor(action);
	      
		// Evaluate the condition
	    if (evaluator.evaluate(actionableNodeRef, actionedUponNodeRef) == true)
	    {
			// Execute the rule
	        executor.execute(actionableNodeRef, actionedUponNodeRef);
	    }
	}
	
	/**
     * Get the action executor instance.
     * 
     * @param action	the action
     * @return			the action executor
     */
    private RuleActionExecuter getActionExecutor(RuleAction action)
    {
        RuleActionExecuter executor = null;
        String executorString = ((RuleActionDefinitionImpl)action.getRuleActionDefinition()).getRuleActionExecutor();
        
        try
        {
            // Create the action executor
            executor = (RuleActionExecuter)Class.forName(executorString).
                    getConstructor(new Class[]{RuleAction.class, ServiceRegistry.class}).
                    newInstance(new Object[]{action, this.serviceRegistry});
        }
        catch(Exception exception)
        {
            // Error creating and initialising
            throw new RuleServiceException("Unable to initialise the rule action executor.", exception);
        }
        
        return executor;
    }

	/**
	 * Get the condition evaluator.
	 * 
	 * @param cond	the rule condition
	 * @return		the rule condition evaluator
	 */
    private RuleConditionEvaluator getConditionEvaluator(RuleCondition cond)
    {
        RuleConditionEvaluator evaluator = null;
        String evaluatorString = ((RuleConditionDefinitionImpl)cond.getRuleConditionDefinition()).getConditionEvaluator();
        
        try
        {
            // Create the condition evaluator
            evaluator = (RuleConditionEvaluator)Class.forName(evaluatorString).
                    getConstructor(new Class[]{RuleCondition.class, ServiceRegistry.class}).
                    newInstance(new Object[]{cond, this.serviceRegistry});
        }
        catch(Exception exception)
        {
            // Error creating and initialising 
            throw new RuleServiceException("Unable to initialise the rule condition evaluator.", exception);
        }
        
        return evaluator;
    }

	/**
	 * Helper class to contain the information about a rule that is pending execution
	 * 
	 * @author Roy Wetherall
	 */
	private class PendingRuleData
	{
		private NodeRef actionableNodeRef;
		private NodeRef actionedUponNodeRef;
		private Rule rule;
		
		public PendingRuleData(NodeRef actionableNodeRef, NodeRef actionedUponNodeRef, Rule rule) 
		{
			this.actionableNodeRef = actionableNodeRef;
			this.actionedUponNodeRef = actionedUponNodeRef;
			this.rule = rule;
		}
		
		public NodeRef getActionableNodeRef() 
		{
			return actionableNodeRef;
		}
		
		public NodeRef getActionedUponNodeRef() 
		{
			return actionedUponNodeRef;
		}
		
		public Rule getRule() 
		{
			return rule;
		}
		
		@Override
		public int hashCode() 
		{
			int i = actionableNodeRef.hashCode();
			i = (i*37) + actionedUponNodeRef.hashCode();
			i = (i*37) + rule.hashCode();
			return i;
		}
		
		@Override
		public boolean equals(Object obj) 
		{
			if (this == obj)
	        {
	            return true;
	        }
	        if (obj instanceof PendingRuleData)
	        {
				PendingRuleData that = (PendingRuleData) obj;
	            return (this.actionableNodeRef.equals(that.actionableNodeRef) &&
	                    this.actionedUponNodeRef.equals(that.actionedUponNodeRef) &&
	                    this.rule.equals(that.rule));
	        }
	        else
	        {
	            return false;
	        }
		}
	}
}
