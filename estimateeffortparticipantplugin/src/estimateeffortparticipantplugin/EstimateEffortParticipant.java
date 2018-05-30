package estimateeffortparticipantplugin;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.team.links.common.IItemReference;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.factory.IReferenceFactory;
import com.ibm.team.process.common.IProcessConfigurationElement;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.advice.AdvisableOperation;
import com.ibm.team.process.common.advice.IReportInfo;
import com.ibm.team.process.common.advice.runtime.IOperationParticipant;
import com.ibm.team.process.common.advice.runtime.IParticipantInfoCollector;
import com.ibm.team.repository.common.IAuditable;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.service.AbstractService;
import com.ibm.team.repository.service.IRepositoryItemService;
import com.ibm.team.workitem.common.ISaveParameter;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.internal.model.WorkItem;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.IAttributeHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemReferences;
import com.ibm.team.workitem.common.model.IWorkItemType;
import com.ibm.team.workitem.common.model.WorkItemEndPoints;
import com.ibm.team.workitem.service.IAuditableServer;
import com.ibm.team.workitem.service.IWorkItemServer;


public class EstimateEffortParticipant extends AbstractService
implements IOperationParticipant  {


	static
	{
		System.out.println(" ***NEW eeeee Estimate Effort Participant Plugin ****");
	}
	private static  final  String WI_TYPE_NAME = "Defect"; 
	private  IAuditableServer auditableServer;
	private static final String WORKITEM_ATTRIBUTE_CORRECTEDESTIMATE = "correctedEstimate";
	private static final String WORKITEM_ATTRIBUTE_TIMESPENT = "timeSpent";

	private IAuditableServer getAuditableServer(){
		if(auditableServer==null)
			auditableServer=getService(IAuditableServer.class);
		return auditableServer;
	}

	private IWorkItemCommon fWorkItemCommon;

	public IWorkItemCommon getfWorkItemCommon() {
		if(fWorkItemCommon==null)
			fWorkItemCommon=getService(IWorkItemCommon.class);
		return fWorkItemCommon;
	}

	private IWorkItemServer  fWorkItemServer;

	public IWorkItemServer getfWorkItemServer() {
		if(fWorkItemServer == null) {
			fWorkItemServer = getService(IWorkItemServer.class);
		}
		return fWorkItemServer;
	}

	private IRepositoryItemService  repositoryItemService;
	private IRepositoryItemService getRepositoryItemService() {
		if(repositoryItemService == null) {
			repositoryItemService = getService(IRepositoryItemService.class);
		}
		return repositoryItemService;
	}

	private static  final  String ATTRIBUTE_NAME= "Estimate";
	@Override
	public void run(AdvisableOperation operation,
			IProcessConfigurationElement participantConfig,
			IParticipantInfoCollector collector, IProgressMonitor monitor)
					throws TeamRepositoryException {
		// TODO Auto-generated method stub

		
		Object data = operation.getOperationData();

		if (!(data instanceof ISaveParameter))
			return;
		System.out.println("=============RUN=================");
		ISaveParameter saveParameter = (ISaveParameter)data;
		if (!(saveParameter.getNewState() instanceof IWorkItem)) {
			return;
		}
		IWorkItem newState = null;
		
		
		IAuditable auditableNew = ((ISaveParameter)data).getNewState();

		try {
			if ((auditableNew instanceof IWorkItem))
			{
				newState = (IWorkItem)auditableNew.getWorkingCopy();
				IProjectAreaHandle projectAreaHandle = (IProjectAreaHandle)newState.getProjectArea();
				IProjectArea projectArea =(IProjectArea)getRepositoryItemService().fetchItem(projectAreaHandle, null);
				System.out.println(" Project area name : " + projectArea.getName());

				System.out.println(" ==========newState================="+newState.getId()); 
				
				
				if(saveParameter.getAdditionalSaveParameters().contains("SAVE_PARENT1"+newState.getId())) {
					
					System.out.println(" enter in IF ");
					
					return ;
				}
				/*	if(!findWITypeByID(newState.getWorkItemType(),projectArea,monitor).equalsIgnoreCase(WI_TYPE_NAME)) {
					System.out.println(" Inside IF ****Clause");

					return ;
				}*/
				
				// Check to see if the work item has a 'Parent'
				IWorkItemHandle parentHandle = findParentHandle(saveParameter, monitor);
				if (parentHandle == null)
					return;
				
			
				// Get the required service interfaces
				fWorkItemServer = getService(IWorkItemServer.class);
				fWorkItemCommon = getService(IWorkItemCommon.class);
				// Roll the child estimates up into the parent estimate
				updateParent(parentHandle, monitor);


			}
		}catch (TeamRepositoryException e) {
			IReportInfo reportinfo = collector.createExceptionInfo("TeamRepositoryException", e);
			collector.addInfo(reportinfo);
		}
	}
	/**
	 * Update the parent from the estimation data of its children.
	 *
	 * @param parentHandle
	 * @param monitor
	 * @throws TeamRepositoryException
	 */
	private void updateParent(IWorkItemHandle parentHandle, IProgressMonitor monitor) throws TeamRepositoryException
	{   System.out.println(" ====================Update Parent ===================");
	// Get the full state of the parent work item so we can edit it
	IWorkItem parent = (IWorkItem)fWorkItemServer.getAuditableCommon().resolveAuditable(parentHandle,IWorkItem.FULL_PROFILE,monitor).getWorkingCopy();
	IAttribute timeSpentAttribute = fWorkItemCommon.findAttribute(parent.getProjectArea(), WORKITEM_ATTRIBUTE_TIMESPENT, monitor);
	IAttribute correctedEstimateAttribute = fWorkItemCommon.findAttribute(parent.getProjectArea(), WORKITEM_ATTRIBUTE_CORRECTEDESTIMATE, monitor);
	long duration = 0; // Estimate
	//IF parent has its own Estimate value

	long timeSpent = 0; // TimeSpent

     long correctedEstimate = 0; // Corrected estimate
	// get all the references
	IWorkItemReferences references = fWorkItemServer.resolveWorkItemReferences(parentHandle, monitor);
	// narrow down to the children
	List listChildReferences = references.getReferences(WorkItemEndPoints.CHILD_WORK_ITEMS);

	IReference parentEndpoint = IReferenceFactory.INSTANCE.createReferenceToItem(parentHandle);
	for (Iterator iterator = listChildReferences.iterator(); iterator.hasNext();) {
		IReference iReference = (IReference) iterator.next();
		ILink link = iReference.getLink();
		if (link.getOtherEndpointDescriptor(parentEndpoint) == WorkItemEndPoints.CHILD_WORK_ITEMS) {
			IWorkItem child = (IWorkItem) fWorkItemServer.getAuditableCommon().resolveAuditable( 
					(IWorkItemHandle)link.getOtherRef(parentEndpoint).resolve(), WorkItem.FULL_PROFILE, monitor);
			long childDuration = child.getDuration();
			timeSpent+=getDuration(child,timeSpentAttribute,monitor);
			correctedEstimate+=getDuration(child,correctedEstimateAttribute,monitor);
			if(childDuration>0)
				duration += childDuration;
		}
	}

	// We want to modify the parent, so get a working copy.
	parent = (IWorkItem)parent.getWorkingCopy();

	// Set the duration on the parent to be the total of child durations
	parent.setDuration(duration);
	System.out.println(" parent estimate value " + duration);

	// Set the corrected estimation
	parent.setValue(correctedEstimateAttribute, correctedEstimate);
	System.out.println(" parent final correctedestimatevalue" + correctedEstimate );

	// Set the time spent/remaining
	parent.setValue(timeSpentAttribute, timeSpent);
	System.out.println("parent final Time Spent value" + timeSpent);

	// Save the work item with an information that could be used to prevent recursive ascent.
	// Additional parameter to avoid recursion
	Set additionalParams = new HashSet();
	additionalParams.add("SAVE_PARENT1"+parent.getId());
	fWorkItemServer.saveWorkItem3(parent, null, null,additionalParams);
	}
	private long getDuration(IWorkItem child, IAttribute attribute, IProgressMonitor monitor) throws TeamRepositoryException {
		long duration = 0;
		if(attribute!=null && child.hasAttribute(attribute)){
			Long tempDuration = (Long)child.getValue(attribute);
			if(tempDuration!=null && tempDuration.longValue()>0)
				return tempDuration.longValue();
		}
		return duration;
	}
	/**
	 * Find the parent of this work item
	 * @param saveParameter
	 * @param monitor
	 * @return a work item handle of the parent or null if a parent does not exist.
	 */
	private IWorkItemHandle findParentHandle(ISaveParameter saveParameter, IProgressMonitor monitor) {

		// Check to see if the references contain a 'Parent' link
		List<IReference> references = saveParameter.getNewReferences().getReferences(WorkItemEndPoints.PARENT_WORK_ITEM);
		if (references.isEmpty())
			return null;

		// Traverse the list of references (there should only be 1 parent) and
		// ensure the reference is to a work item then return a handle to that work item
		for (IReference reference: references)
			if (reference.isItemReference() && ((IItemReference) reference).getReferencedItem() instanceof IWorkItemHandle)
				return (IWorkItemHandle)((IItemReference) reference).getReferencedItem();
		return null;
	}
}