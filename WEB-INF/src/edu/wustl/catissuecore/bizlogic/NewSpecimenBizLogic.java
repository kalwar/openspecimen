/**
 * <p>Title: NewSpecimenHDAO Class>
 * <p>Description:	NewSpecimenBizLogicHDAO is used to add new specimen information into the database using Hibernate.</p>
 * Copyright:    Copyright (c) year
 * Company: Washington University, School of Medicine, St. Louis.
 * @author Aniruddha Phadnis
 * @version 1.00
 * Created on Jul 21, 2005
 */

package edu.wustl.catissuecore.bizlogic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.HibernateException;

import edu.wustl.catissuecore.TaskTimeCalculater;

import edu.wustl.catissuecore.actionForm.NewSpecimenForm;
import edu.wustl.catissuecore.domain.AbstractSpecimenCollectionGroup;
import edu.wustl.catissuecore.domain.Address;
import edu.wustl.catissuecore.domain.Biohazard;
import edu.wustl.catissuecore.domain.CellSpecimen;
import edu.wustl.catissuecore.domain.CollectionEventParameters;
import edu.wustl.catissuecore.domain.CollectionProtocol;
import edu.wustl.catissuecore.domain.ConsentTierStatus;
import edu.wustl.catissuecore.domain.DisposalEventParameters;
import edu.wustl.catissuecore.domain.DistributedItem;
import edu.wustl.catissuecore.domain.ExternalIdentifier;
import edu.wustl.catissuecore.domain.FluidSpecimen;
import edu.wustl.catissuecore.domain.MolecularSpecimen;
import edu.wustl.catissuecore.domain.Quantity;
import edu.wustl.catissuecore.domain.QuantityInCount;
import edu.wustl.catissuecore.domain.QuantityInGram;
import edu.wustl.catissuecore.domain.QuantityInMicrogram;
import edu.wustl.catissuecore.domain.QuantityInMilliliter;
import edu.wustl.catissuecore.domain.ReceivedEventParameters;
import edu.wustl.catissuecore.domain.Specimen;
import edu.wustl.catissuecore.domain.SpecimenCharacteristics;
import edu.wustl.catissuecore.domain.SpecimenCollectionGroup;
import edu.wustl.catissuecore.domain.SpecimenCollectionRequirementGroup;
import edu.wustl.catissuecore.domain.SpecimenEventParameters;
import edu.wustl.catissuecore.domain.StorageContainer;
import edu.wustl.catissuecore.domain.TissueSpecimen;
import edu.wustl.catissuecore.domain.User;
import edu.wustl.catissuecore.namegenerator.BarcodeGenerator;
import edu.wustl.catissuecore.namegenerator.BarcodeGeneratorFactory;
import edu.wustl.catissuecore.namegenerator.LabelGenerator;
import edu.wustl.catissuecore.namegenerator.LabelGeneratorFactory;
import edu.wustl.catissuecore.util.ApiSearchUtil;
import edu.wustl.catissuecore.util.EventsUtil;
import edu.wustl.catissuecore.util.MultipleSpecimenValidationUtil;
import edu.wustl.catissuecore.util.StorageContainerUtil;
import edu.wustl.catissuecore.util.WithdrawConsentUtil;
import edu.wustl.catissuecore.util.global.Constants;
import edu.wustl.catissuecore.util.global.Utility;
import edu.wustl.common.actionForm.IValueObject;
import edu.wustl.common.beans.SessionDataBean;
import edu.wustl.common.bizlogic.DefaultBizLogic;
import edu.wustl.common.cde.CDEManager;
import edu.wustl.common.dao.AbstractDAO;
import edu.wustl.common.dao.DAO;
import edu.wustl.common.dao.DAOFactory;
import edu.wustl.common.dao.HibernateDAO;
import edu.wustl.common.dao.JDBCDAO;
import edu.wustl.common.domain.AbstractDomainObject;
import edu.wustl.common.exception.BizLogicException;
import edu.wustl.common.security.SecurityManager;
import edu.wustl.common.security.exceptions.SMException;
import edu.wustl.common.security.exceptions.UserNotAuthorizedException;
import edu.wustl.common.util.Permissions;
import edu.wustl.common.util.dbManager.DAOException;
import edu.wustl.common.util.dbManager.HibernateMetaData;
import edu.wustl.common.util.global.ApplicationProperties;
import edu.wustl.common.util.global.Validator;
import edu.wustl.common.util.logger.Logger;

/**
 * NewSpecimenHDAO is used to add new specimen information into the database using Hibernate.
 * @author aniruddha_phadnis
 */

public class NewSpecimenBizLogic extends DefaultBizLogic
{
	private Map<Long, Collection> containerHoldsSpecimenClasses = new HashMap<Long, Collection>();
	private Map<Long, Collection> containerHoldsCPs = new HashMap<Long, Collection>();
	private HashSet<String> storageContainerIds = new HashSet<String>();
	private SecurityManager securityManager = new SecurityManager(this.getClass());
	private boolean cpbased = false;

	/**
	 * Saves the storageType object in the database.
	 * @param obj The storageType object to be saved.
	 * @param session The session in which the object is saved.
	 * @throws DAOException 
	 */
	protected void insert(Object obj, DAO dao, SessionDataBean sessionDataBean) throws DAOException, UserNotAuthorizedException
	{

		if (!isCpbased())
		{
			isAuthorise(sessionDataBean.getUserName());
		}

		if (obj.getClass().hashCode() == LinkedHashMap.class.hashCode())
		{
			insertMultipleSpecimen((LinkedHashMap<Specimen, List<Specimen>>) obj, dao, sessionDataBean);
		}

		else if (obj instanceof Specimen)
		{
			insertSingleSpecimen((Specimen) obj, dao, sessionDataBean, false);

			//Inserting authorization data 
			Specimen specimen = (Specimen) obj;
			Set protectionObjects = new HashSet();
			protectionObjects.add(specimen);
			if (specimen.getSpecimenCharacteristics() != null)
			{
				protectionObjects.add(specimen.getSpecimenCharacteristics());
			}
			try
			{
				securityManager.insertAuthorizationData(null, protectionObjects, getDynamicGroups(specimen.getSpecimenCollectionGroup()));
			}
			catch (SMException e)
			{
				throw handleSMException(e);
			}

		}
		else
		{
			throw new DAOException("Object should be either specimen or LinkedHashMap " + "of specimen objects.");
		}

	}

	private void isAuthorise(String userName) throws UserNotAuthorizedException
	{
		try
		{
			if (!securityManager.isAuthorized(userName, Specimen.class.getName(), Permissions.CREATE))
			{
				throw new UserNotAuthorizedException("User is not authorised to create specimens");
			}
		}
		catch (SMException exception)
		{
			throw new UserNotAuthorizedException(exception.getMessage(), exception);
		}

	}

	/**
	 * Insert multiple specimen into the data base.
	 * @param specimenList
	 * @param dao
	 * @param sessionDataBean
	 * @throws DAOException
	 * @throws UserNotAuthorizedException
	 */
	private void insertMultipleSpecimen(LinkedHashMap<Specimen, List<Specimen>> specimenMap, DAO dao, SessionDataBean sessionDataBean)
			throws DAOException, UserNotAuthorizedException
	{
		List specimenList = new ArrayList();
		Iterator specimenIterator = specimenMap.keySet().iterator();
		int count = 0;

		while (specimenIterator.hasNext())
		{
			TaskTimeCalculater mulSpec = TaskTimeCalculater.startTask("Multiple specimen ", NewSpecimenBizLogic.class);
			count++;
			Specimen specimen = (Specimen) specimenIterator.next();

			/**
			 * Name : Ashish Gupta
			 * Reviewer's Name: Sachin Lale
			 * Bug ID: 3262
			 * Patch ID: 3262_3
			 * See also: 1-4
			 * Description: Adding Default Events if user has not entered them
			 */
			Collection specimenEventColl = specimen.getSpecimenEventCollection();
			if (sessionDataBean != null && (specimenEventColl == null || specimenEventColl.isEmpty()))
			{
				setDefaultEventsToSpecimen(specimen, sessionDataBean);
			}
			/**
			 * Start: Change for API Search   --- Jitendra 06/10/2006
			 * In Case of Api Search, previoulsy it was failing since there was default class level initialization 
			 * on domain object. For example in User object, it was initialized as protected String lastName=""; 
			 * So we removed default class level initialization on domain object and are initializing in method
			 * setAllValues() of domain object. But in case of Api Search, default values will not get set 
			 * since setAllValues() method of domainObject will not get called. To avoid null pointer exception,
			 * we are setting the default values same as we were setting in setAllValues() method of domainObject.
			 */
			ApiSearchUtil.setSpecimenDefault(specimen);
			//End:- Change for API Search

			Long parentSpecimenId = specimen.getId();

			//resetId(specimen);

			/**
			 * Patch ID: 3835_1_21
			 * See also: 1_1 to 1_5
			 * Description : set created date to collected event date if this specimen is not derived one in case of multiple specimen.
			 */

			if ((specimen.getParentSpecimen() == null))
			{
				setCreatedOnDate(specimen);
			}

			/*
			 * Name:Ashish Gupta
			 * Reviewer: Santosh Chandak
			 * Bug ID: 2989
			 * Pathch ID:2989_1
			 * Description:If parent specimen is present, retriving its events 
			 * */
			//Setting parent specimens events if derived 
			if (specimen.getParentSpecimen() != null && specimen.getParentSpecimen().getId() != null && specimen.getParentSpecimen().getId() > 0)
			{
				TaskTimeCalculater setParentData = TaskTimeCalculater.startTask("Set parent Data", NewSpecimenBizLogic.class);
				setParentSpecimenData(specimen, dao);
				TaskTimeCalculater.endTask(setParentData);
			}

			try
			{
				storageContainerIds.clear();
				setStorageLocationToNewSpecimen(dao, specimen, sessionDataBean, true);
				insertSingleSpecimen(specimen, dao, sessionDataBean, true);
				specimenList.add(specimen);
			}
			catch (DAOException daoException)
			{
				String message = " (This message is for Specimen number " + count + ")";
				daoException.setSupportingMessage(message);
				throw daoException;
			}
			catch (SMException e)
			{
				String message = " (This message is for Specimen number " + count + ")";
				e.printStackTrace();
				DAOException daoException = new DAOException(e);
				daoException.setSupportingMessage(message);
				throw daoException;

			}

			List derivedSpecimens = (List) specimenMap.get(specimen);
			if (derivedSpecimens == null)
			{
				TaskTimeCalculater.endTask(mulSpec);
				continue;
			}
			//insert derived specimens
			for (int i = 0; i < derivedSpecimens.size(); i++)
			{
				Specimen derivedSpecimen = (Specimen) derivedSpecimens.get(i);
				//resetId(derivedSpecimen);
				derivedSpecimen.setParentSpecimen(specimen);
				derivedSpecimen.setSpecimenCollectionGroup(specimen.getSpecimenCollectionGroup());

				try
				{
					setParentSpecimenData(derivedSpecimen, dao);
					insertSingleSpecimen(derivedSpecimen, dao, sessionDataBean, true);
					specimenList.add(derivedSpecimen);
				}
				catch (DAOException daoException)
				{
					int j = i + 1;
					String message = " (This message is for Derived Specimen " + j + " of Parent Specimen number " + count + ")";
					daoException.setSupportingMessage(message);
					throw daoException;
				}
			}
			TaskTimeCalculater.endTask(mulSpec);
		}

		//inserting authorization data 
		authenticateSpecimens(specimenList);

	}

	/**
	 * @param specimenList
	 * @throws DAOException
	 */
	private void authenticateSpecimens(List specimenList) throws DAOException
	{
		Iterator itr = specimenList.iterator();
		TaskTimeCalculater specAuth = TaskTimeCalculater.startTask("Specimen insert Authenticate (" + specimenList.size() + ")",
				NewSpecimenBizLogic.class);
		String dynamicGroups[] = null;
		try
		{
			Set protectionObjects = new HashSet();
			AbstractSpecimenCollectionGroup collectionGroup = null;
			while (itr.hasNext())
			{
				Specimen specimen = (Specimen) itr.next();
				protectionObjects.add(specimen);

				if (specimen.getSpecimenCharacteristics() != null)
				{
					protectionObjects.add(specimen.getSpecimenCharacteristics());
				}
				collectionGroup = specimen.getSpecimenCollectionGroup();
			}
			if (collectionGroup != null)
			{
				dynamicGroups = getDynamicGroups(collectionGroup);
				securityManager.insertAuthorizationData(null, protectionObjects, dynamicGroups);
			}
		}
		catch (SMException e)
		{
			throw handleSMException(e);
		}
		finally
		{
			TaskTimeCalculater.endTask(specAuth);
		}
	}

	/**
	 * @param specimen
	 * @param dao
	 * @throws DAOException
	 * This method retrieves the parent specimen events and sets them in the parent specimen
	 */
	private void setParentSpecimenData(Specimen specimen, DAO dao) throws DAOException
	{
		Specimen parent = (Specimen) dao.retrieve(Specimen.class.getName(), specimen.getParentSpecimen().getId());

		//		retrieving the parent specimen events
		if (parent.getSpecimenEventCollection() != null)
		{

			String sourceObjectName = SpecimenEventParameters.class.getName();
			String columnName = Constants.COLUMN_NAME_SPECIMEN;
			long whereColumnValue = parent.getId().longValue();
			/*List parentSpecimenEventColl = dao.retrieve(sourceObjectName, columnName, whereColumnValue);
			 
			 if(parentSpecimenEventColl == null || parent.getSpecimenEventCollection().isEmpty())
			 {
			 parent.setSpecimenEventCollection(new HashSet());
			 }
			 else
			 {	//Converting list to hashset
			 Collection tempColl = new HashSet();    		
			 tempColl.addAll(parentSpecimenEventColl);        		
			 parent.setSpecimenEventCollection(tempColl);
			 }*/
		}
		else
		{
			parent.setSpecimenEventCollection(new HashSet());
		}

		//Added by Poornima
		specimen.setParentSpecimen(parent);
		specimen.setSpecimenCharacteristics(parent.getSpecimenCharacteristics());

		//Ashish - 8/6/07 - retriving parent scg for performance improvement
		//		AbstractSpecimenCollectionGroup parentSCG = (AbstractSpecimenCollectionGroup)dao.retrieveAttribute(Specimen.class.getName(),parent.getId() , Constants.COLUMN_NAME_SCG);
		//		specimen.setSpecimenCollectionGroup(parent.getSpecimenCollectionGroup());
		// set event parameters from parent specimen - added by Ashwin for bug id# 2476
		//		specimen.setSpecimenEventCollection(populateDeriveSpecimenEventCollection(parent,specimen));
		specimen.setPathologicalStatus(parent.getPathologicalStatus());
		if (parent != null)
		{
			Set set = new HashSet();

			Collection biohazardCollection = parent.getBiohazardCollection();
			if (biohazardCollection != null)
			{
				Iterator it = biohazardCollection.iterator();
				while (it.hasNext())
				{
					Biohazard hazard = (Biohazard) it.next();
					set.add(hazard);
				}
			}
			specimen.setBiohazardCollection(set);
		}
	}

	//This method sets the created on date = collection date
	private void setCreatedOnDate(Specimen specimen)
	{
		Collection specimenEventsCollection = specimen.getSpecimenEventCollection();
		if (specimenEventsCollection != null)
		{
			Iterator specimenEventsCollectionIterator = specimenEventsCollection.iterator();
			while (specimenEventsCollectionIterator.hasNext())
			{
				Object eventObject = specimenEventsCollectionIterator.next();
				if (eventObject instanceof CollectionEventParameters)
				{
					CollectionEventParameters collEventParam = (CollectionEventParameters) eventObject;
					specimen.setCreatedOn(collEventParam.getTimestamp());
				}
			}
		}
	}

	/**
	 * @param specimen
	 * @param sessionDataBean
	 * This method sets the default events to specimens if they are null
	 */
	private void setDefaultEventsToSpecimen(Specimen specimen, SessionDataBean sessionDataBean)
	{
		Collection specimenEventColl = new HashSet();
		User user = new User();
		user.setId(sessionDataBean.getUserId());
		CollectionEventParameters collectionEventParameters = EventsUtil.populateCollectionEventParameters(user);
		collectionEventParameters.setSpecimen(specimen);
		specimenEventColl.add(collectionEventParameters);

		ReceivedEventParameters receivedEventParameters = EventsUtil.populateReceivedEventParameters(user);
		receivedEventParameters.setSpecimen(specimen);
		specimenEventColl.add(receivedEventParameters);

		specimen.setSpecimenEventCollection(specimenEventColl);
	}

	/**
	 * By Rahul Ner
	 * @param specimen
	 */
	private void resetId(Specimen specimen)
	{
		specimen.setId(null);
		Iterator childItr = null;

		if (specimen.getSpecimenEventCollection() != null)
		{

			childItr = specimen.getSpecimenEventCollection().iterator();
			while (childItr.hasNext())
			{
				SpecimenEventParameters eventParams = (SpecimenEventParameters) childItr.next();
				eventParams.setSpecimen(specimen);
				eventParams.setId(null);
			}

		}

		if (specimen.getExternalIdentifierCollection() != null)
		{
			childItr = specimen.getExternalIdentifierCollection().iterator();
			while (childItr.hasNext())
			{
				ExternalIdentifier externalIdentifier = (ExternalIdentifier) childItr.next();
				externalIdentifier.setSpecimen(specimen);
				externalIdentifier.setId(null);
			}
		}

		if (specimen.getBiohazardCollection() != null)
		{
			childItr = specimen.getBiohazardCollection().iterator();
			while (childItr.hasNext())
			{
				Biohazard biohazard = (Biohazard) childItr.next();
				//biohazard.setId(null);
			}
		}
	}

	/**
	 * This method gives the error message.
	 * This method is overrided for customizing error message
	 * @param ex - DAOException
	 * @param obj - Object
	 * @return - error message string
	 */
	public String getErrorMessage(DAOException daoException, Object obj, String operation)
	{
		if (obj instanceof HashMap)
		{
			obj = new Specimen();
		}
		String supportingMessage = daoException.getSupportingMessage();
		String formatedException = formatException(daoException.getWrapException(), obj, operation);
		if (supportingMessage != null && formatedException != null)
		{
			formatedException += supportingMessage;
		}
		if (formatedException == null)
		{
			formatedException = daoException.getMessage();
			if (supportingMessage != null)
				formatedException += supportingMessage;
		}
		return formatedException;
	}

	/**
	 * Insert single specimen into the data base.
	 * @param specimen
	 * @param dao
	 * @param sessionDataBean
	 * @param partOfMulipleSpecimen
	 * @throws DAOException
	 * @throws UserNotAuthorizedException 
	 */
	private void insertSingleSpecimen(Specimen specimen, DAO dao, SessionDataBean sessionDataBean, boolean partOfMulipleSpecimen)
			throws DAOException, UserNotAuthorizedException
	{
		try
		{
			/**
			 * Start: Change for API Search   --- Jitendra 06/10/2006
			 * In Case of Api Search, previoulsy it was failing since there was default class level initialization 
			 * on domain object. For example in User object, it was initialized as protected String lastName=""; 
			 * So we removed default class level initialization on domain object and are initializing in method
			 * setAllValues() of domain object. But in case of Api Search, default values will not get set 
			 * since setAllValues() method of domainObject will not get called. To avoid null pointer exception,
			 * we are setting the default values same as we were setting in setAllValues() method of domainObject.
			 */

			ApiSearchUtil.setSpecimenDefault(specimen);

			Specimen parentSpecimen = specimen.getParentSpecimen();
			setParentSCG(specimen, dao, parentSpecimen);

			setExternalIdentifiers(specimen, specimen.getExternalIdentifierCollection());

			/**
			 * Name: Abhishek Mehta 
			 * Bug ID: 5558
			 * Patch ID: 5558_2
			 * See also: 1-3 
			 * Description : Earlier the available quantity for specimens that haven't been collected yet is greater than 0.
			 */
			if (specimen.getAvailableQuantity() != null && specimen.getAvailableQuantity().getValue().doubleValue() == 0 && Constants.COLLECTION_STATUS_COLLECTED.equalsIgnoreCase(specimen.getCollectionStatus()))
			{
				specimen.setAvailableQuantity(specimen.getInitialQuantity());
			}

			if ((specimen.getAvailableQuantity() != null && specimen.getAvailableQuantity().getValue().doubleValue() == 0)
					|| specimen.getCollectionStatus() == null || Constants.COLLECTION_STATUS_PENDING.equalsIgnoreCase(specimen.getCollectionStatus()))
			{
				specimen.setAvailable(new Boolean(false));
			}
			else
			{
				specimen.setAvailable(new Boolean(true));
			}

			if (specimen.getLineage() == null)
			{
				specimen.setLineage(Constants.NEW_SPECIMEN);
			}
			//Setting the created on date = collection date if lineage = NEW_SPECIMEN
			setCreatedOnDate(specimen);

			setSpecimenAttributes(dao, specimen, sessionDataBean, partOfMulipleSpecimen);

			generateLabel(specimen);
			generateBarCode(specimen);
			dao.insert(specimen.getSpecimenCharacteristics(), sessionDataBean, false, false);
			dao.insert(specimen, sessionDataBean, false, false);
			//protectionObjects.add(specimen);

			/*if (specimen.getSpecimenCharacteristics() != null)
			 {
			 protectionObjects.add(specimen.getSpecimenCharacteristics());
			 }*/

			//Mandar : 17-july-06 : autoevents start
			//			Collection specimenEventsCollection = specimen.getSpecimenEventCollection();
			//			Iterator specimenEventsCollectionIterator = specimenEventsCollection.iterator();
			//			while (specimenEventsCollectionIterator.hasNext())
			//			{
			//
			//				Object eventObject = specimenEventsCollectionIterator.next();
			//				
			//
			//				if (eventObject instanceof CollectionEventParameters)
			//				{
			//					CollectionEventParameters collectionEventParameters = (CollectionEventParameters) eventObject;
			//					collectionEventParameters.setSpecimen(specimen);
			//					//collectionEventParameters.setId(null);
			//					dao.insert(collectionEventParameters, sessionDataBean, true, true);
			//
			//				}
			//				else if (eventObject instanceof ReceivedEventParameters)
			//				{
			//					ReceivedEventParameters receivedEventParameters = (ReceivedEventParameters) eventObject;
			//					receivedEventParameters.setSpecimen(specimen);
			//					//receivedEventParameters.setId(null);
			//					dao.insert(receivedEventParameters, sessionDataBean, true, true);
			//
			//				}
			//
			//			}
			//Mandar : 17-july-06 : autoevents end
			//Inserting data for Authorization
			//SecurityManager.getInstance(this.getClass()).insertAuthorizationData(null, protectionObjects, getDynamicGroups(specimen));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new DAOException(e.getMessage());
		}

	}

	/**
	 * @param specimen
	 * @throws DAOException
	 */
	private void generateBarCode(Specimen specimen) throws DAOException
	{
		if (edu.wustl.catissuecore.util.global.Variables.isSpecimenBarcodeGeneratorAvl)
		{
			//Setting Name from Id
			if ((specimen.getBarcode() == null || specimen.getBarcode().equals("")) && !specimen.getIsCollectionProtocolRequirement())
			{

				try
				{
					BarcodeGenerator spBarcodeGenerator = BarcodeGeneratorFactory.getInstance(Constants.SPECIMEN_BARCODE_GENERATOR_PROPERTY_NAME);
					spBarcodeGenerator.setBarcode(specimen);
				}
				catch (BizLogicException e)
				{
					throw new DAOException(e.getMessage());
				}
			}
		}
	}

	/**
	 * @param specimen
	 * @param externalIdentifierCollection
	 */
	private void setExternalIdentifiers(Specimen specimen, Collection externalIdentifierCollection)
	{
		if (externalIdentifierCollection != null)
		{
			if (externalIdentifierCollection.isEmpty()) //Dummy entry added for query
			{
				setEmptyExternalIdentifier(specimen, externalIdentifierCollection);
			}
			else
			{
				setSpecimenToExternalIdentifier(specimen, externalIdentifierCollection);
			}
		}
		else
		{
			//Dummy entry added for query.
			externalIdentifierCollection = new HashSet();
			setEmptyExternalIdentifier(specimen, externalIdentifierCollection);
			specimen.setExternalIdentifierCollection(externalIdentifierCollection);
		}
	}

	/**
	 * @param specimen
	 * @param dao
	 * @param parentSpecimen
	 * @throws DAOException
	 */
	private void setParentSCG(Specimen specimen, DAO dao, Specimen parentSpecimen) throws DAOException
	{
		if (parentSpecimen != null)
		{
			if (parentSpecimen.getId() == null)
			{
				List parentSpecimenList = dao.retrieve(Specimen.class.getName(), "label", parentSpecimen.getLabel());

				if (parentSpecimenList != null && !parentSpecimenList.isEmpty())
				{
					parentSpecimen = (Specimen) parentSpecimenList.get(0);
				}
			}
			specimen.setParentSpecimen(parentSpecimen);
			specimen.setSpecimenCollectionGroup(parentSpecimen.getSpecimenCollectionGroup());

		}
		//End:- Change for API Search
	}

	/**
	 * @param specimen
	 * @throws DAOException
	 */
	private void generateLabel(Specimen specimen) throws DAOException
	{
		/**
		 * Name:Falguni Sachde
		 * Reviewer: Sachin lale
		 * Call Specimen label generator if automatic generation is specified 
		 */
		if (edu.wustl.catissuecore.util.global.Variables.isSpecimenLabelGeneratorAvl)
		{
			//Setting Name from Id
			if ((specimen.getLabel() == null || specimen.getLabel().equals("")) && !specimen.getIsCollectionProtocolRequirement())
			{

				try
				{
					TaskTimeCalculater labelGen = TaskTimeCalculater.startTask("Time required for label Generator", NewSpecimenBizLogic.class);

					LabelGenerator spLblGenerator = LabelGeneratorFactory.getInstance(Constants.SPECIMEN_LABEL_GENERATOR_PROPERTY_NAME);
					spLblGenerator.setLabel(specimen);
					TaskTimeCalculater.endTask(labelGen);
				}
				catch (BizLogicException e)
				{
					throw new DAOException(e.getMessage());
				}
			}
		}
	}

	/**
	 * @param specimen
	 * @param externalIdentifierCollection
	 */
	private void setSpecimenToExternalIdentifier(Specimen specimen, Collection externalIdentifierCollection)
	{
		/**
		 *  Bug 3007 - Santosh
		 */
		Iterator it = externalIdentifierCollection.iterator();
		while (it.hasNext())
		{
			ExternalIdentifier exId = (ExternalIdentifier) it.next();
			exId.setSpecimen(specimen);
			//					dao.insert(exId, sessionDataBean, true, true);
		}
	}

	/**
	 * @param specimen
	 * @param externalIdentifierCollection
	 */
	private void setEmptyExternalIdentifier(Specimen specimen, Collection externalIdentifierCollection)
	{
		ExternalIdentifier exId = new ExternalIdentifier();

		exId.setName(null);
		exId.setValue(null);
		exId.setSpecimen(specimen);
		externalIdentifierCollection.add(exId);
	}

	synchronized public void postInsert(Object obj, DAO dao, SessionDataBean sessionDataBean) throws DAOException, UserNotAuthorizedException
	{

		Map containerMap = getStorageContainerMap();
		if (obj instanceof HashMap)
		{
			HashMap specimenMap = (HashMap) obj;
			Iterator specimenIterator = specimenMap.keySet().iterator();
			while (specimenIterator.hasNext())
			{
				Specimen specimen = (Specimen) specimenIterator.next();
				updateStorageLocations((TreeMap) containerMap, specimen);
				List derivedSpecimens = (List) specimenMap.get(specimen);

				if (derivedSpecimens != null)
				{
					for (int i = 0; i < derivedSpecimens.size(); i++)
					{

						Specimen derivedSpecimen = (Specimen) derivedSpecimens.get(i);
						updateStorageLocations((TreeMap) containerMap, derivedSpecimen);
					}
				}
			}
		}
		else
		{
			updateStorageLocations((TreeMap) containerMap, (Specimen) obj);
		}

	}

	synchronized public void postInsert(Collection speCollection, DAO dao, SessionDataBean sessionDataBean) throws DAOException,
			UserNotAuthorizedException
	{

		Map containerMap = getStorageContainerMap();
		Iterator specimenIterator = speCollection.iterator();
		while (specimenIterator.hasNext())
		{
			Specimen specimen = (Specimen) specimenIterator.next();
			updateStorageLocations((TreeMap) containerMap, specimen);
			Collection childSpecimens = specimen.getChildrenSpecimen();

			if (childSpecimens != null)
			{
				Iterator childSpecimenIterator = childSpecimens.iterator();
				while (childSpecimenIterator.hasNext())
				{
					Specimen derivedSpecimen = (Specimen) childSpecimenIterator.next();
					updateStorageLocations((TreeMap) containerMap, derivedSpecimen);
				}
			}
		}

	}

	private Map getStorageContainerMap()
	{
		Map containerMap = null;
		try
		{
			containerMap = StorageContainerUtil.getContainerMapFromCache();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return containerMap;
	}

	/**
	 * This method gets called after update method. Any logic after updating into database can be included here.
	 * @param dao the dao object
	 * @param currentObj The object to be updated.
	 * @param oldObj The old object.
	 * @param sessionDataBean session specific data
	 * @throws DAOException
	 * @throws UserNotAuthorizedException
	 * */
	protected void postUpdate(DAO dao, Object currentObj, Object oldObj, SessionDataBean sessionDataBean) throws BizLogicException,
			UserNotAuthorizedException
	{
		/**
		 * Bug 3094 --> This jdbc query updates all the aliquots of a specimen, saperate query is written to improve the performance
		 */

		Specimen currentSpecimen = (Specimen) currentObj;
		Specimen oldSpecimen = (Specimen) oldObj;
		String type = currentSpecimen.getType();
		String pathologicalStatus = currentSpecimen.getPathologicalStatus();
		String id = currentSpecimen.getId().toString();
		if (!currentSpecimen.getPathologicalStatus().equals(oldSpecimen.getPathologicalStatus())
				|| !currentSpecimen.getType().equals(oldSpecimen.getType()))
		{
			try
			{
				JDBCDAO jdbcDao = (JDBCDAO) DAOFactory.getInstance().getDAO(Constants.JDBC_DAO);
				jdbcDao.openSession(null);

				String queryStr = "UPDATE CATISSUE_SPECIMEN SET TYPE = '" + type + "',PATHOLOGICAL_STATUS = '" + pathologicalStatus
						+ "' WHERE LINEAGE = 'ALIQUOT' AND PARENT_SPECIMEN_ID ='" + id + "';";

				jdbcDao.executeUpdate(queryStr);
				jdbcDao.closeSession();
			}
			catch (Exception e)
			{
				Logger.out.debug("Exception occured while updating aliquots");
			}
		}
	}

	void updateStorageLocations(TreeMap containerMap, Specimen specimen)
	{
		try
		{
			if (specimen.getStorageContainer() != null)
			{

				StorageContainerUtil.deleteSinglePositionInContainerMap(specimen.getStorageContainer(), containerMap, specimen
						.getPositionDimensionOne().intValue(), specimen.getPositionDimensionTwo().intValue());

			}
		}
		catch (Exception e)
		{

		}
	}

	protected String[] getDynamicGroups(AbstractSpecimenCollectionGroup obj) throws SMException
	{
		TaskTimeCalculater getDynaGrps = TaskTimeCalculater.startTask("DynamicGroup", NewSpecimenBizLogic.class);

		String[] dynamicGroups = new String[1];

		dynamicGroups[0] = securityManager.getProtectionGroupByName(obj, Constants.getCollectionProtocolPGName(null));
		Logger.out.debug("Dynamic Group name: " + dynamicGroups[0]);
		TaskTimeCalculater.endTask(getDynaGrps);
		return dynamicGroups;
	}

	protected void chkContainerValidForSpecimen(StorageContainer container, Specimen specimen, DAO dao) throws DAOException
	{
		Collection holdsSpecimenClassColl = containerHoldsSpecimenClasses.get(container.getId());
		if (holdsSpecimenClassColl == null)
		{
			holdsSpecimenClassColl = (Collection) dao.retrieveAttribute(StorageContainer.class.getName(), container.getId(),
					"elements(holdsSpecimenClassCollection)");
			containerHoldsSpecimenClasses.put(container.getId(), holdsSpecimenClassColl);
		}
		boolean aa = holdsSpecimenClassColl.contains(specimen.getClassName());
		if (!aa)
		{
			throw new DAOException("This Storage Container cannot hold " + specimen.getClassName() + " Specimen ");
		}

		Collection collectionProtColl = containerHoldsCPs.get(container.getId());
		if (collectionProtColl == null)
		{
			collectionProtColl = (Collection) dao.retrieveAttribute(StorageContainer.class.getName(), container.getId(),
					"elements(collectionProtocolCollection)");
			containerHoldsCPs.put(container.getId(), collectionProtColl);
		}
		//SpecimenCollectionGroup scg = (SpecimenCollectionGroup) dao.retrieveAttribute(Specimen.class.getName(),specimen.getId(),"specimenCollectionGroup");
		//CollectionProtocol protocol = (CollectionProtocol)dao.retrieveAttribute(SpecimenCollectionGroup.class.getName(), scg.getId(), "collectionProtocolRegistration.collectionProtocol");
		SpecimenCollectionGroup scg = null;
		CollectionProtocol protocol = null;
		if (specimen.getSpecimenCollectionGroup() != null)
		{
			scg = (SpecimenCollectionGroup) specimen.getSpecimenCollectionGroup();
		}
		else if (specimen.getId() != null)
		{
			scg = (SpecimenCollectionGroup) dao.retrieveAttribute(Specimen.class.getName(), specimen.getId(), "specimenCollectionGroup");
		}
		if (scg != null)
			protocol = (CollectionProtocol) dao.retrieveAttribute(SpecimenCollectionGroup.class.getName(), scg.getId(),
					"collectionProtocolRegistration.collectionProtocol");
		if (protocol != null)
		{
			boolean bb = collectionProtColl.isEmpty();
			if (!bb)
			{
				bb = collectionProtColl.contains(protocol);
			}
			if (!bb)
			{
				throw new DAOException("This Storage Container cannot hold specimen of collection protocol " + protocol.getTitle());
			}
		}
		else
		{
			throw new DAOException("This Collection Protocol not found");
		}
	}

	private SpecimenCollectionGroup loadSpecimenCollectionGroup(Long specimenID, DAO dao) throws DAOException
	{
		//get list of Participant's names
		String sourceObjectName = Specimen.class.getName();
		String[] selectedColumn = {"specimenCollectionGroup." + Constants.SYSTEM_IDENTIFIER};
		String whereColumnName[] = {Constants.SYSTEM_IDENTIFIER};
		String whereColumnCondition[] = {"="};
		Object whereColumnValue[] = {specimenID};
		String joinCondition = Constants.AND_JOIN_CONDITION;

		List list = dao.retrieve(sourceObjectName, selectedColumn, whereColumnName, whereColumnCondition, whereColumnValue, joinCondition);
		if (!list.isEmpty())
		{
			Long specimenCollectionGroupId = (Long) list.get(0);
			SpecimenCollectionGroup specimenCollectionGroup = new SpecimenCollectionGroup();
			specimenCollectionGroup.setId(specimenCollectionGroupId);
			return specimenCollectionGroup;
		}
		return null;
	}

	private SpecimenCharacteristics loadSpecimenCharacteristics(Long specimenID, DAO dao) throws DAOException
	{
		//get list of Participant's names
		String sourceObjectName = Specimen.class.getName();
		String[] selectedColumn = {"specimenCharacteristics." + Constants.SYSTEM_IDENTIFIER};
		String whereColumnName[] = {Constants.SYSTEM_IDENTIFIER};
		String whereColumnCondition[] = {"="};
		Object whereColumnValue[] = {specimenID};
		String joinCondition = Constants.AND_JOIN_CONDITION;

		List list = dao.retrieve(sourceObjectName, selectedColumn, whereColumnName, whereColumnCondition, whereColumnValue, joinCondition);
		if (!list.isEmpty())
		{
			Long specimenCharacteristicsId = (Long) list.get(0);
			SpecimenCharacteristics specimenCharacteristics = new SpecimenCharacteristics();
			specimenCharacteristics.setId(specimenCharacteristicsId);
			return specimenCharacteristics;

			//return (SpecimenCharacteristics)list.get(0);
		}
		return null;
	}

	private void setAvailableQuantity(Specimen obj, Specimen oldObj) throws DAOException
	{
		if (obj instanceof TissueSpecimen)
		{
			Logger.out.debug("In TissueSpecimen");
			TissueSpecimen tissueSpecimenObj = (TissueSpecimen) obj;
			Specimen tissueSpecimenOldObj = (Specimen) oldObj;
			// get new qunatity modifed by user
			double newQty = Double.parseDouble(tissueSpecimenObj.getInitialQuantity().toString());//tissueSpecimenObj.getQuantityInGram().doubleValue();
			// get old qunatity from database
			double oldQty = Double.parseDouble(tissueSpecimenOldObj.getInitialQuantity().toString());//tissueSpecimenOldObj.getQuantityInGram().doubleValue();
			Logger.out.debug("New Qty: " + newQty + " Old Qty: " + oldQty);
			// get Available qty
			double oldAvailableQty = Double.parseDouble(tissueSpecimenOldObj.getAvailableQuantity().toString());//tissueSpecimenOldObj.getAvailableQuantityInGram().doubleValue();

			double distQty = 0;
			double newAvailableQty = 0;
			// Distributed Qty = Old_Qty - Old_Available_Qty
			distQty = oldQty - oldAvailableQty;

			// New_Available_Qty = New_Qty - Distributed_Qty
			newAvailableQty = newQty - distQty;
			Logger.out.debug("Dist Qty: " + distQty + " New Available Qty: " + newAvailableQty);
			if (newAvailableQty < 0)
			{
				throw new DAOException("Newly modified Quantity '" + newQty + "' should not be less than current Distributed Quantity '" + distQty
						+ "'");
			}
			else
			{
				// set new available quantity
				tissueSpecimenObj.setAvailableQuantity(new QuantityInGram(newAvailableQty));//tissueSpecimenObj.setAvailableQuantityInGram(new Double(newAvailableQty));
			}

		}
		else if (obj instanceof MolecularSpecimen)
		{
			Logger.out.debug("In MolecularSpecimen");
			MolecularSpecimen molecularSpecimenObj = (MolecularSpecimen) obj;
			Specimen molecularSpecimenOldObj = (Specimen) oldObj;
			// get new qunatity modifed by user
			double newQty = Double.parseDouble(molecularSpecimenObj.getInitialQuantity().toString());//molecularSpecimenObj.getQuantityInMicrogram().doubleValue();
			// get old qunatity from database
			double oldQty = Double.parseDouble(molecularSpecimenOldObj.getInitialQuantity().toString());//molecularSpecimenOldObj.getQuantityInMicrogram().doubleValue();
			Logger.out.debug("New Qty: " + newQty + " Old Qty: " + oldQty);
			// get Available qty
			double oldAvailableQty = Double.parseDouble(molecularSpecimenOldObj.getAvailableQuantity().toString());//molecularSpecimenOldObj.getAvailableQuantityInMicrogram().doubleValue();

			double distQty = 0;
			double newAvailableQty = 0;
			// Distributed Qty = Old_Qty - Old_Available_Qty
			distQty = oldQty - oldAvailableQty;

			// New_Available_Qty = New_Qty - Distributed_Qty
			newAvailableQty = newQty - distQty;
			Logger.out.debug("Dist Qty: " + distQty + " New Available Qty: " + newAvailableQty);
			if (newAvailableQty < 0)
			{
				throw new DAOException("Newly modified Quantity '" + newQty + "' should not be less than current Distributed Quantity '" + distQty
						+ "'");
			}
			else
			{
				// set new available quantity
				molecularSpecimenObj.setAvailableQuantity(new QuantityInMicrogram(newAvailableQty));//molecularSpecimenObj.setAvailableQuantityInMicrogram(new Double(newAvailableQty));
			}
		}
		else if (obj instanceof CellSpecimen)
		{
			Logger.out.debug("In CellSpecimen");
			CellSpecimen cellSpecimenObj = (CellSpecimen) obj;
			Specimen cellSpecimenOldObj = (Specimen) oldObj;
			// get new qunatity modifed by user
			long newQty = (long) Double.parseDouble(cellSpecimenObj.getInitialQuantity().toString());//cellSpecimenObj.getQuantityInCellCount().intValue();
			// get old qunatity from database
			long oldQty = (long) Double.parseDouble(cellSpecimenOldObj.getInitialQuantity().toString());//cellSpecimenOldObj.getQuantityInCellCount().intValue();
			Logger.out.debug("New Qty: " + newQty + " Old Qty: " + oldQty);
			// get Available qty
			long oldAvailableQty = (long) Double.parseDouble(cellSpecimenOldObj.getAvailableQuantity().toString());//cellSpecimenOldObj.getAvailableQuantityInCellCount().intValue();

			long distQty = 0;
			long newAvailableQty = 0;
			// Distributed Qty = Old_Qty - Old_Available_Qty
			distQty = oldQty - oldAvailableQty;

			// New_Available_Qty = New_Qty - Distributed_Qty
			newAvailableQty = newQty - distQty;
			Logger.out.debug("Dist Qty: " + distQty + " New Available Qty: " + newAvailableQty);
			if (newAvailableQty < 0)
			{
				throw new DAOException("Newly modified Quantity '" + newQty + "' should not be less than current Distributed Quantity '" + distQty
						+ "'");
			}
			else
			{
				// set new available quantity
				cellSpecimenObj.setAvailableQuantity(new QuantityInCount(newAvailableQty));//cellSpecimenObj.setAvailableQuantityInCellCount(new Integer(newAvailableQty));
			}
		}
		else if (obj instanceof FluidSpecimen)
		{
			Logger.out.debug("In FluidSpecimen");
			FluidSpecimen fluidSpecimenObj = (FluidSpecimen) obj;
			Specimen fluidSpecimenOldObj = (Specimen) oldObj;
			// get new qunatity modifed by user
			double newQty = Double.parseDouble(fluidSpecimenObj.getInitialQuantity().toString());//fluidSpecimenObj.getQuantityInMilliliter().doubleValue();
			// get old qunatity from database
			double oldQty = Double.parseDouble(fluidSpecimenOldObj.getInitialQuantity().toString());//fluidSpecimenOldObj.getQuantityInMilliliter().doubleValue();
			Logger.out.debug("New Qty: " + newQty + " Old Qty: " + oldQty);
			// get Available qty
			double oldAvailableQty = Double.parseDouble(fluidSpecimenOldObj.getAvailableQuantity().toString());//fluidSpecimenOldObj.getAvailableQuantityInMilliliter().doubleValue();

			double distQty = 0;
			double newAvailableQty = 0;
			// Distributed Qty = Old_Qty - Old_Available_Qty
			distQty = oldQty - oldAvailableQty;

			// New_Available_Qty = New_Qty - Distributed_Qty
			newAvailableQty = newQty - distQty;
			Logger.out.debug("Dist Qty: " + distQty + " New Available Qty: " + newAvailableQty);
			if (newAvailableQty < 0)
			{
				throw new DAOException("Newly modified Quantity '" + newQty + "' should not be less than current Distributed Quantity '" + distQty
						+ "'");
			}
			else
			{
				fluidSpecimenObj.setAvailableQuantity(new QuantityInMilliliter(newAvailableQty));//fluidSpecimenObj.setAvailableQuantityInMilliliter(new Double(newAvailableQty));
			}
		}
	}

	/**
	 * Updates the persistent object in the database.
	 * @param obj The object to be updated.
	 * @param session The session in which the object is saved.
	 * @throws DAOException 
	 * @throws HibernateException Exception thrown during hibernate operations.
	 */
	public void update(DAO dao, Object obj, Object oldObj, SessionDataBean sessionDataBean) throws DAOException, UserNotAuthorizedException
	{

		Specimen specimen = (Specimen) obj;
		Specimen specimenOld = (Specimen) HibernateMetaData.getProxyObjectImpl(oldObj);
		//Specimen specimenOld = (Specimen) oldObj;

		boolean isInitQtyChange = false;
		boolean isInitAvlChange = false;
		/**
		 * Name:Virender Mehta
		 * Reviewer: Sachin lale
		 * This methos will retrive and set SCG Id from SCG name.
		 */
		if (specimen.getLineage().equals(Constants.NEW_SPECIMEN))
		{
			//retriveSCGIdFromSCGName(specimen,dao);
		}
		//retrive and set parentSpecimenId
		if (specimen.getParentSpecimen() != null && specimen.getParentSpecimen().getId() == null)
		{
			Long parentSpecimenId = (Long) dao.retrieveAttribute(Specimen.class.getName(), specimen.getId(), "parentSpecimen.id");
			specimen.getParentSpecimen().setId(parentSpecimenId);

		}

		/**
		 * Start: Change for API Search   --- Jitendra 06/10/2006
		 * In Case of Api Search, previoulsy it was failing since there was default class level initialization 
		 * on domain object. For example in User object, it was initialized as protected String lastName=""; 
		 * So we removed default class level initialization on domain object and are initializing in method
		 * setAllValues() of domain object. But in case of Api Search, default values will not get set 
		 * since setAllValues() method of domainObject will not get called. To avoid null pointer exception,
		 * we are setting the default values same as we were setting in setAllValues() method of domainObject.
		 */
		ApiSearchUtil.setSpecimenDefault(specimen);

		//Added for api Search 
		if (isStoragePositionChanged(specimenOld, specimen))
		{
			throw new DAOException("Storage Position should not be changed while updating the specimen");
		}
		if (!specimenOld.getLineage().equals(specimen.getLineage()))
		{
			throw new DAOException("Lineage should not be changed while updating the specimen");
		}
		/**
		 * Name : Virender
		 * Reviewer: Sachin lale
		 * Calling Domain object from Proxy Object
		 */
		Specimen specimenImplObj = (Specimen) HibernateMetaData.getProxyObjectImpl(specimenOld);
		if (!specimenImplObj.getClassName().equals(specimen.getClassName()))
		{
			throw new DAOException("Class should not be changed while updating the specimen");
		}
		//		 if(specimenOld.getAvailableQuantity().getValue().longValue() != specimen.getAvailableQuantity().getValue().longValue())
		//		 {
		//		 	throw new DAOException("Available Quantity should not be changed while updating the specimen");
		//		 }

		//End:- Change for API Search

		// get old qunatity from database			
		double oldAvailableQty = Double.parseDouble(specimenOld.getAvailableQuantity().toString());//tissueSpecimenOldObj.getAvailableQuantityInGram().doubleValue();
		double oldQty = Double.parseDouble(specimenOld.getInitialQuantity().toString());//tissueSpecimenOldObj.getQuantityInGram().doubleValue();

		//get new qunatity modifed by user
		double newAvailableQty = Double.parseDouble(specimen.getAvailableQuantity().toString());//tissueSpecimenOldObj.getAvailableQuantityInGram().doubleValue();
		double newQty = Double.parseDouble(specimen.getInitialQuantity().toString());//tissueSpecimenObj.getQuantityInGram().doubleValue();

		if ((oldAvailableQty - newAvailableQty) != 0)
		{
			isInitAvlChange = true;
		}
		else if ((oldQty - newQty) != 0)
		{
			isInitQtyChange = true;
		}
		if (isInitQtyChange && !isInitAvlChange)
		{
			setAvailableQuantity(specimen, specimenOld);
		}

		if (specimen.isParentChanged())
		{
			//Check whether continer is moved to one of its sub container.
			if (isUnderSubSpecimen(specimen, specimen.getParentSpecimen().getId()))
			{
				throw new DAOException(ApplicationProperties.getValue("errors.specimen.under.subspecimen"));
			}
			Logger.out.debug("Loading ParentSpecimen: " + specimen.getParentSpecimen().getId());

			// check for closed ParentSpecimen
			checkStatus(dao, specimen.getParentSpecimen(), "Parent Specimen");

			SpecimenCollectionGroup scg = loadSpecimenCollectionGroup(specimen.getParentSpecimen().getId(), dao);

			specimen.setSpecimenCollectionGroup(scg);
		}

		//check for closed Specimen Collection Group
		if (!specimen.getSpecimenCollectionGroup().getId().equals(specimenOld.getSpecimenCollectionGroup().getId()))
			checkStatus(dao, specimen.getSpecimenCollectionGroup(), "Specimen Collection Group");

		/**
		 * Name:Virender Mehta
		 * Reviewer: Aarti Sharma
		 * */
		if (!Constants.ALIQUOT.equals(specimen.getLineage()))//specimen instanceof OriginalSpecimen)
		{
			dao.update(specimen.getSpecimenCharacteristics(), sessionDataBean, true, true, false);
		}
		setSpecimenGroupForSubSpecimen(specimen, specimen.getSpecimenCollectionGroup(), dao);

		//Consent Tracking
		if (!specimen.getConsentWithdrawalOption().equalsIgnoreCase(Constants.WITHDRAW_RESPONSE_NOACTION))
		{
			updateConsentWithdrawStatus(specimen, specimenImplObj, dao, sessionDataBean);
		}
		else if (!specimen.getApplyChangesTo().equalsIgnoreCase(Constants.APPLY_NONE))
		{
			updateConsentStatus(specimen, dao, specimenImplObj);
		}
		//Consent Tracking	
		//Mandar: 16-Jan-07

		/**
		 * Refer bug 3269 
		 * 1. If quantity of old object > 0 and it is unavailable, it was marked 
		 *    unavailale by user. 
		 * 2. If quantity of old object = 0, we can assume that it is unavailable because its quantity 
		 *    has become 0.
		 */

		if ((specimen.getAvailableQuantity() != null && specimen.getAvailableQuantity().getValue().doubleValue() == 0)
				|| specimen.getCollectionStatus() == null || specimen.getCollectionStatus().equalsIgnoreCase(Constants.COLLECTION_STATUS_PENDING))
		{
			specimen.setAvailable(new Boolean(false));
		}
		else if (specimenOld.getAvailableQuantity() != null && specimenOld.getAvailableQuantity().getValue().doubleValue() == 0)
		{
			// quantity of old object is zero and that of current is nonzero
			specimen.setAvailable(new Boolean(true));
		}
		else
		{
			specimen.setAvailable(new Boolean(true));
		}

		dao.update(specimen, sessionDataBean, true, false, false);//dao.update(specimen, sessionDataBean, true, true, false);

		//Audit of Specimen.
		dao.audit(obj, oldObj, sessionDataBean, true);

		//Audit of Specimen Characteristics.
		dao.audit(specimen.getSpecimenCharacteristics(), specimenOld.getSpecimenCharacteristics(), sessionDataBean, true);
		Collection oldExternalIdentifierCollection = specimenOld.getExternalIdentifierCollection();
		//dao.retrieve(ExternalIdentifier.class.getName(),"specimen",specimenOld.getId()); 
		Collection externalIdentifierCollection = specimen.getExternalIdentifierCollection();
		if (externalIdentifierCollection != null)
		{
			Iterator it = externalIdentifierCollection.iterator();
			while (it.hasNext())
			{
				ExternalIdentifier exId = (ExternalIdentifier) it.next();
				exId.setSpecimen(specimen);
				dao.update(exId, sessionDataBean, true, true, false);

				ExternalIdentifier oldExId = (ExternalIdentifier) getCorrespondingOldObject(oldExternalIdentifierCollection, exId.getId());
				dao.audit(exId, oldExId, sessionDataBean, true);
			}
		}

		//Disable functionality
		Logger.out.debug("specimen.getActivityStatus() " + specimen.getActivityStatus());
		if (specimen.getConsentWithdrawalOption().equalsIgnoreCase(Constants.WITHDRAW_RESPONSE_NOACTION))
		{
			if (specimen.getActivityStatus().equals(Constants.ACTIVITY_STATUS_DISABLED))
			{
				//			 check for disabling a specimen 
				boolean disposalEventPresent = false;
				Collection eventCollection = specimen.getSpecimenEventCollection();
				Iterator itr = eventCollection.iterator();
				while (itr.hasNext())
				{
					Object eventObject = itr.next();
					if (eventObject instanceof DisposalEventParameters)
					{
						disposalEventPresent = true;
						break;
					}
				}
				if (!disposalEventPresent)
				{
					throw new DAOException(ApplicationProperties.getValue("errors.specimen.not.disabled.no.disposalevent"));
				}

				setDisableToSubSpecimen(specimen);
				Logger.out.debug("specimen.getActivityStatus() " + specimen.getActivityStatus());
				Long specimenIDArr[] = new Long[1];
				specimenIDArr[0] = specimen.getId();

				disableSubSpecimens(dao, specimenIDArr);
			}
		}
	}

	private boolean isUnderSubSpecimen(Specimen specimen, Long parentSpecimenID)
	{
		if (specimen != null)
		{
			Iterator iterator = specimen.getChildrenSpecimen().iterator();
			while (iterator.hasNext())
			{
				Specimen childSpecimen = (Specimen) iterator.next();
				//Logger.out.debug("SUB CONTINER container "+parentContainerID.longValue()+" "+container.getId().longValue()+"  "+(parentContainerID.longValue()==container.getId().longValue()));
				if (parentSpecimenID.longValue() == childSpecimen.getId().longValue())
					return true;
				if (isUnderSubSpecimen(childSpecimen, parentSpecimenID))
					return true;
			}
		}
		return false;
	}

	/**
	 * Name: Virender Mehta
	 * Reviewer: Aarti Sharma
	 * Retrive Child Specimen from parent Specimen 
	 * @param specimen
	 * @param specimenCollectionGroup
	 * @param specimenCharacteristics
	 * @param dao
	 * @throws DAOException
	 */
	private void setSpecimenGroupForSubSpecimen(Specimen specimen, AbstractSpecimenCollectionGroup specimenCollectionGroup, DAO dao)
			throws DAOException
	{
		if (specimen != null)
		{
			Logger.out.debug("specimen() " + specimen.getId());
			Collection childrenSpecimen = (Collection) dao
					.retrieveAttribute(Specimen.class.getName(), specimen.getId(), "elements(childrenSpecimen)");
			Logger.out.debug("specimen.getChildrenContainerCollection() " + childrenSpecimen.size());
			Iterator iterator = childrenSpecimen.iterator();
			while (iterator.hasNext())
			{
				Specimen childSpecimen = (Specimen) iterator.next();
				childSpecimen.getSpecimenCharacteristics();
				childSpecimen.setSpecimenCollectionGroup(specimenCollectionGroup);
				setSpecimenGroupForSubSpecimen(childSpecimen, specimenCollectionGroup, dao);

			}
		}
	}

	//  TODO TO BE REMOVED 
	private void setDisableToSubSpecimen(Specimen specimen)
	{
		if (specimen != null)
		{
			Iterator iterator = specimen.getChildrenSpecimen().iterator();
			while (iterator.hasNext())
			{
				Specimen childSpecimen = (Specimen) iterator.next();
				childSpecimen.setActivityStatus(Constants.ACTIVITY_STATUS_DISABLED);
				setDisableToSubSpecimen(childSpecimen);
			}
		}
	}

	private void setSpecimenAttributes(DAO dao, Specimen specimen, SessionDataBean sessionDataBean, boolean partOfMultipleSpecimen)
			throws DAOException, SMException
	{

		specimen.setActivityStatus(Constants.ACTIVITY_STATUS_ACTIVE);
		// set barcode to null in case it is blank
		if (specimen.getBarcode() != null && specimen.getBarcode().trim().equals(""))
		{
			specimen.setBarcode(null);
		}

		// TODO
		//Load & set Specimen Collection Group if present
		if (specimen.getSpecimenCollectionGroup() != null)
		{
			SpecimenCollectionGroup specimenCollectionGroupObj = null;
			if (partOfMultipleSpecimen)
			{
				/*String sourceObjectName = SpecimenCollectionGroup.class.getName();
				 String[] selectColumnName = {"id"};
				 String[] whereColumnName = {"name"}; 
				 String[] whereColumnCondition = {"="};
				 Object[] whereColumnValue = {specimen.getSpecimenCollectionGroup().getName()};
				 String joinCondition = null;

				 List list = dao.retrieve(sourceObjectName, selectColumnName, whereColumnName, whereColumnCondition, whereColumnValue, joinCondition);

				 specimenCollectionGroupObj = new SpecimenCollectionGroup();
				 specimenCollectionGroupObj.setId((Long)list.get(0));*/
				Collection consentTierStatusCollection = null;

				if (specimen.getSpecimenCollectionGroup().getGroupName() != null
						&& !Constants.COLLECTION_STATUS_PENDING.equalsIgnoreCase(specimen.getCollectionStatus()))
				{
					List spgList = dao.retrieve(SpecimenCollectionGroup.class.getName(), Constants.NAME, specimen.getSpecimenCollectionGroup()
							.getGroupName());
					SpecimenCollectionGroup scg = (SpecimenCollectionGroup) spgList.get(0);
					specimenCollectionGroupObj = (SpecimenCollectionGroup) HibernateMetaData.getProxyObjectImpl(scg);

					//Resolved lazy ----  specimenCollectionGroupObj.getConsentTierStatusCollection();
					consentTierStatusCollection = specimenCollectionGroupObj.getConsentTierStatusCollection();
					//consentTierStatusCollection= (Collection)dao.retrieveAttribute(SpecimenCollectionGroup.class.getName(),specimenCollectionGroupObj.getId(), "elements(consentTierStatusCollection)" );
				}

				if (consentTierStatusCollection != null)
				{
					Collection consentTierStatusCollectionForSpecimen = new HashSet();
					Iterator itr = consentTierStatusCollection.iterator();
					while (itr.hasNext())
					{
						ConsentTierStatus conentTierStatus = (ConsentTierStatus) itr.next();
						ConsentTierStatus consentTierStatusForSpecimen = new ConsentTierStatus();
						consentTierStatusForSpecimen.setStatus(conentTierStatus.getStatus());
						consentTierStatusForSpecimen.setConsentTier(conentTierStatus.getConsentTier());
						consentTierStatusCollectionForSpecimen.add(consentTierStatusForSpecimen);
					}
					specimen.setConsentTierStatusCollection(consentTierStatusCollectionForSpecimen);
				}
			}
			else
			{
				specimenCollectionGroupObj = new SpecimenCollectionGroup();
				specimenCollectionGroupObj.setId(specimen.getSpecimenCollectionGroup().getId());

			}
			if (specimenCollectionGroupObj != null)
			{
				/*if (specimenCollectionGroupObj.getActivityStatus().equals(Constants.ACTIVITY_STATUS_CLOSED))
				 {
				 throw new DAOException("Specimen Collection Group " + ApplicationProperties.getValue("error.object.closed"));
				 }*/
				checkStatus(dao, specimenCollectionGroupObj, "Specimen Collection Group");
				//Commented as we should not set specific SCG object in business Logic to specimen.  
				//specimen.setSpecimenCollectionGroup(specimenCollectionGroupObj);
			}
		}

		//Load & set Parent Specimen if present
		if (specimen.getParentSpecimen() != null)
		{
			//			Object parentSpecimenObj = dao.retrieve(Specimen.class.getName(), specimen.getParentSpecimen().getId());
			//			if (parentSpecimenObj != null)
			//			{
			Specimen parentSpecimen = new Specimen();
			parentSpecimen.setId(specimen.getParentSpecimen().getId());
			// check for closed Parent Specimen
			if (specimen.getParentSpecimen().getActivityStatus() == null)
			{
				checkStatus(dao, parentSpecimen, "Parent Specimen");
			}
			else if (!specimen.getParentSpecimen().getActivityStatus().equalsIgnoreCase(Constants.ACTIVITY_STATUS_ACTIVE))
			{
				throw new DAOException("Parent Specimen " + ApplicationProperties.getValue("error.object.closed"));
			}

			if (specimen.getLineage() == null)
			{
				specimen.setLineage(Constants.DERIVED_SPECIMEN);
			}
			// set parent specimen event parameters -- added by Ashwin for bug id# 2476
			specimen.setSpecimenEventCollection(populateDeriveSpecimenEventCollection(specimen.getParentSpecimen(), specimen));
			//			}
		}

		//Setting the Biohazard Collection
		Set set = new HashSet();
		Collection biohazardCollection = specimen.getBiohazardCollection();
		if (biohazardCollection != null)
		{
			Iterator it = biohazardCollection.iterator();
			while (it.hasNext())
			{
				Biohazard hazard = (Biohazard) it.next();
				Logger.out.debug("hazard.getId() " + hazard.getId());
				Object bioObj = dao.retrieve(Biohazard.class.getName(), hazard.getId());
				if (bioObj != null)
				{
					Biohazard hazardObj = (Biohazard) bioObj;
					set.add(hazardObj);
				}
			}
		}
		specimen.setBiohazardCollection(set);

		//Load & set Storage Container
		if (!partOfMultipleSpecimen)
		{
			setStorageLocationToNewSpecimen(dao, specimen, sessionDataBean, partOfMultipleSpecimen);
		}

	}

	/**
	 * @param dao
	 * @param specimen
	 * @param sessionDataBean 
	 * @param partOfMultipleSpecimen 
	 * @throws DAOException
	 */
	private void setStorageLocationToNewSpecimen(DAO dao, Specimen specimen, SessionDataBean sessionDataBean, boolean partOfMultipleSpecimen)
			throws DAOException, SMException
	{
		if (specimen.getStorageContainer() != null)
		{

			//retrieveStorageContainerObject(dao, specimen.getStorageContainer(), specimen.getStorageContainer().getId());
			StorageContainer storageContainerObj = new StorageContainer();


			String joinCondition = null;

			if (specimen.getStorageContainer().getId() != null)
			{
				String sourceObjectName = StorageContainer.class.getName();
				String[] whereColumnCondition = {"="};

				storageContainerObj.setId(specimen.getStorageContainer().getId());
				String[] selectColumnName = {"name"};
				String[] whereColumnName = {"id"};
				Object[] whereColumnValue = {specimen.getStorageContainer().getId()};
				List list = dao.retrieve(sourceObjectName, selectColumnName, whereColumnName, whereColumnCondition, whereColumnValue, joinCondition);

				if (!list.isEmpty())
				{
					storageContainerObj.setName((String) list.get(0));
				}
			}
			else
			{
				setStorageContainerId(dao, specimen, storageContainerObj);

			}

			// check for closed Storage Container
			checkStatus(dao, storageContainerObj, "Storage Container");
			chkContainerValidForSpecimen(storageContainerObj, specimen, dao);
			StorageContainerBizLogic storageContainerBizLogic = (StorageContainerBizLogic) BizLogicFactory.getInstance().getBizLogic(
					Constants.STORAGE_CONTAINER_FORM_ID);
			
			//kalpana : check closed site
			storageContainerBizLogic.checkClosedSite(dao, storageContainerObj.getId(), "Container Site");

			if (specimen.getPositionDimensionOne() == null || specimen.getPositionDimensionTwo() == null)
			{

				LinkedList<Integer> positionValues = StorageContainerUtil.getFirstAvailablePositionsInContainer(storageContainerObj,
						getStorageContainerMap(), storageContainerIds);

				specimen.setPositionDimensionOne(positionValues.get(0));

				specimen.setPositionDimensionTwo(positionValues.get(1));

			}

			//kalpana: Bug#6001
			String storageValue = storageContainerObj.getName()+":"+specimen.getPositionDimensionOne()+" ,"+ 
			specimen.getPositionDimensionTwo();
			
			if (!storageContainerIds.contains(storageValue))
			{
					storageContainerIds.add(storageValue);
			}

			// --- check for all validations on the storage container.
			storageContainerBizLogic.checkContainer(dao, storageContainerObj.getId().toString(), specimen.getPositionDimensionOne().toString(),
					specimen.getPositionDimensionTwo().toString(), sessionDataBean, partOfMultipleSpecimen);
			specimen.setStorageContainer(storageContainerObj);

		}

		if (specimen.getChildrenSpecimen() != null)
		{
			setChildrenSpecimenStorage(specimen.getChildrenSpecimen(), dao, sessionDataBean, partOfMultipleSpecimen);
		}

	}

	private void setStorageContainerId(DAO dao, Specimen specimen, StorageContainer storageContainerObj) throws DAOException
	{
		String sourceObjectName = StorageContainer.class.getName();
		String[] whereColumnCondition = {"="};		
		storageContainerObj.setName(specimen.getStorageContainer().getName());
		String[] selectColumnName = {"id"};
		String[] whereColumnName = {"name"};
		Object[] whereColumnValue = {specimen.getStorageContainer().getName()};
		List list = dao.retrieve(sourceObjectName, selectColumnName, whereColumnName, whereColumnCondition, whereColumnValue, null);

		if (!list.isEmpty())
		{
			storageContainerObj.setId((Long) list.get(0));
		}
	}

	private void setChildrenSpecimenStorage(Collection specimenCollection, DAO dao, SessionDataBean sessionDataBean, boolean partOfMultipleSpecimen)
			throws DAOException, SMException
	{
		Iterator iterator = specimenCollection.iterator();
		while (iterator.hasNext())
		{
			Specimen specimen = (Specimen) iterator.next();
			setStorageLocationToNewSpecimen(dao, specimen, sessionDataBean, partOfMultipleSpecimen);

		}
	}
	
	//kalpana Bug#6001
	private void allocatePositionForChildrenSpecimen(Collection specimenCollection)
	{
		Iterator iterator = specimenCollection.iterator();
		while(iterator.hasNext())
		{
			Specimen specimen = (Specimen) iterator.next();
			if (specimen.getPositionDimensionOne() != null || 
					specimen.getPositionDimensionTwo() != null)
			{
				
				String storageValue = specimen.getStorageContainer().getName()+":"+specimen.getPositionDimensionOne()+" ,"+ 
				specimen.getPositionDimensionTwo();				
				storageContainerIds.add(storageValue);
				
			}
			
		}
	}

	public void disableRelatedObjectsForSpecimenCollectionGroup(DAO dao, Long specimenCollectionGroupArr[]) throws DAOException
	{
		Logger.out.debug("disableRelatedObjects NewSpecimenBizLogic");
		List listOfSpecimenId = super.disableObjects(dao, Specimen.class, "specimenCollectionGroup", "CATISSUE_SPECIMEN",
				"SPECIMEN_COLLECTION_GROUP_ID", specimenCollectionGroupArr);
		if (!listOfSpecimenId.isEmpty())
		{
			disableSubSpecimens(dao, Utility.toLongArray(listOfSpecimenId));
		}
	}

	//    public void disableRelatedObjectsForStorageContainer(DAO dao, Long storageContainerIdArr[])throws DAOException 
	//    {
	//    	Logger.out.debug("disableRelatedObjectsForStorageContainer NewSpecimenBizLogic");
	//    	List listOfSpecimenId = super.disableObjects(dao, Specimen.class, "storageContainer", 
	//    			"CATISSUE_SPECIMEN", "STORAGE_CONTAINER_IDENTIFIER", storageContainerIdArr);
	//    	if(!listOfSpecimenId.isEmpty())
	//    	{
	//    		disableSubSpecimens(dao,Utility.toLongArray(listOfSpecimenId));
	//    	}
	//    }

	private void disableSubSpecimens(DAO dao, Long speIDArr[]) throws DAOException
	{
		List listOfSubElement = super.disableObjects(dao, Specimen.class, "parentSpecimen", "CATISSUE_SPECIMEN", "PARENT_SPECIMEN_ID", speIDArr);

		if (listOfSubElement.isEmpty())
			return;
		disableSubSpecimens(dao, Utility.toLongArray(listOfSubElement));
	}

	/**
	 * @param dao
	 * @param privilegeName
	 * @param longs
	 * @param userId
	 * @throws DAOException
	 * @throws SMException
	 */
	public void assignPrivilegeToRelatedObjectsForSCG(DAO dao, String privilegeName, Long[] specimenCollectionGroupArr, Long userId, String roleId,
			boolean assignToUser, boolean assignOperation) throws SMException, DAOException
	{
		Logger.out.debug("assignPrivilegeToRelatedObjectsForSCG NewSpecimenBizLogic");
		List listOfSpecimenId = super.getRelatedObjects(dao, Specimen.class, "specimenCollectionGroup", specimenCollectionGroupArr);
		if (!listOfSpecimenId.isEmpty())
		{
			super.setPrivilege(dao, privilegeName, Specimen.class, Utility.toLongArray(listOfSpecimenId), userId, roleId, assignToUser,
					assignOperation);
			List specimenCharacteristicsIds = super.getRelatedObjects(dao, Specimen.class, new String[]{"specimenCharacteristics."
					+ Constants.SYSTEM_IDENTIFIER}, new String[]{Constants.SYSTEM_IDENTIFIER}, Utility.toLongArray(listOfSpecimenId));
			super.setPrivilege(dao, privilegeName, Address.class, Utility.toLongArray(specimenCharacteristicsIds), userId, roleId, assignToUser,
					assignOperation);

			assignPrivilegeToSubSpecimens(dao, privilegeName, Specimen.class, Utility.toLongArray(listOfSpecimenId), userId, roleId, assignToUser,
					assignOperation);
		}
	}

	/**
	 * @param dao
	 * @param privilegeName
	 * @param class1
	 * @param longs
	 * @param userId
	 * @throws DAOException
	 * @throws SMException
	 */
	private void assignPrivilegeToSubSpecimens(DAO dao, String privilegeName, Class class1, Long[] speIDArr, Long userId, String roleId,
			boolean assignToUser, boolean assignOperation) throws SMException, DAOException
	{
		List listOfSubElement = super.getRelatedObjects(dao, Specimen.class, "parentSpecimen", speIDArr);

		if (listOfSubElement.isEmpty())
			return;
		super.setPrivilege(dao, privilegeName, Specimen.class, Utility.toLongArray(listOfSubElement), userId, roleId, assignToUser, assignOperation);
		List specimenCharacteristicsIds = super.getRelatedObjects(dao, Specimen.class, new String[]{"specimenCharacteristics."
				+ Constants.SYSTEM_IDENTIFIER}, new String[]{Constants.SYSTEM_IDENTIFIER}, Utility.toLongArray(listOfSubElement));
		super.setPrivilege(dao, privilegeName, Address.class, Utility.toLongArray(specimenCharacteristicsIds), userId, roleId, assignToUser,
				assignOperation);

		assignPrivilegeToSubSpecimens(dao, privilegeName, Specimen.class, Utility.toLongArray(listOfSubElement), userId, roleId, assignToUser,
				assignOperation);
	}

	public void setPrivilege(DAO dao, String privilegeName, Class objectType, Long[] objectIds, Long userId, String roleId, boolean assignToUser,
			boolean assignOperation) throws SMException, DAOException
	{
		super.setPrivilege(dao, privilegeName, objectType, objectIds, userId, roleId, assignToUser, assignOperation);
		List specimenCharacteristicsIds = super.getRelatedObjects(dao, Specimen.class, new String[]{"specimenCharacteristics."
				+ Constants.SYSTEM_IDENTIFIER}, new String[]{Constants.SYSTEM_IDENTIFIER}, objectIds);
		super.setPrivilege(dao, privilegeName, Address.class, Utility.toLongArray(specimenCharacteristicsIds), userId, roleId, assignToUser,
				assignOperation);

		assignPrivilegeToSubSpecimens(dao, privilegeName, Specimen.class, objectIds, userId, roleId, assignToUser, assignOperation);
	}

	// validation code here
	/**
	 * @see edu.wustl.common.bizlogic.IBizLogic#setPrivilege(DAO, String, Class, Long[], Long, String, boolean)
	 * @param dao
	 * @param privilegeName
	 * @param objectIds
	 * @param userId
	 * @param roleId
	 * @param assignToUser
	 * @throws SMException
	 * @throws DAOException
	 */
	public void assignPrivilegeToRelatedObjectsForDistributedItem(DAO dao, String privilegeName, Long[] objectIds, Long userId, String roleId,
			boolean assignToUser, boolean assignOperation) throws SMException, DAOException
	{
		String[] selectColumnNames = {"specimen.id"};
		String[] whereColumnNames = {"id"};
		List listOfSubElement = super.getRelatedObjects(dao, DistributedItem.class, selectColumnNames, whereColumnNames, objectIds);
		if (!listOfSubElement.isEmpty())
		{
			super.setPrivilege(dao, privilegeName, Specimen.class, Utility.toLongArray(listOfSubElement), userId, roleId, assignToUser,
					assignOperation);
		}
	}

	/**
	 * Overriding the parent class's method to validate the enumerated attribute values
	 */
	protected boolean validate(Object obj, DAO dao, String operation) throws DAOException
	{
		boolean result;

		if (obj instanceof Map)
		{
			//validation on multiple specimens are performed in MultipleSpecimenValidationUtil, so dont require to perform the bizlogic validations.
			//return true;
			//result = validateMultipleSpecimen((Map) obj, dao, operation);
			return MultipleSpecimenValidationUtil.validateMultipleSpecimen((Map) obj, dao, operation);
		}
		else
		{
			result = validateSingleSpecimen((Specimen) obj, dao, operation, false);
		}
		return result;

	}

	/**
	 * validates single specimen. 
	 */
	private boolean validateSingleSpecimen(Specimen specimen, DAO dao, String operation, boolean partOfMulipleSpecimen) throws DAOException
	{
		//Added by Ashish		
		//Logger.out.debug("Start-Inside validate method of specimen bizlogic");
		if (specimen == null)
		{
			throw new DAOException(ApplicationProperties.getValue("specimen.object.null.err.msg", "Specimen"));
		}

		Validator validator = new Validator();
		/**
		 * Name: Virender Mehta
		 * Reviewer name: Sachin Lale
		 * Description: Resolved Performance Issue,retrive scg obj explicitly  
		 * 
		 */
		if (specimen.getSpecimenCollectionGroup() == null
				&& ((specimen.getSpecimenCollectionGroup().getId() == null || specimen.getSpecimenCollectionGroup().getId().equals("-1")) || (specimen
						.getSpecimenCollectionGroup().getGroupName() == null || specimen.getSpecimenCollectionGroup().getGroupName().equals(""))))
		{
			String message = ApplicationProperties.getValue("specimen.specimenCollectionGroup");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", message));
		}
		if (specimen.getParentSpecimen() != null
				&& (specimen.getParentSpecimen().getLabel() == null || validator.isEmpty(specimen.getParentSpecimen().getLabel())))
		{
			String message = ApplicationProperties.getValue("createSpecimen.parent");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", message));
		}

		/*		
		 *if (validator.isEmpty(specimen.getLabel()))
		 {
		 String message = ApplicationProperties.getValue("specimen.label");
		 throw new DAOException(ApplicationProperties.getValue("errors.item.required", message));
		 }*/

		if (validator.isEmpty(specimen.getClassName()))
		{
			String message = ApplicationProperties.getValue("specimen.type");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", message));
		}

		if (validator.isEmpty(specimen.getType()))
		{
			String message = ApplicationProperties.getValue("specimen.subType");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", message));
		}

		if (specimen.getStorageContainer() != null
				&& (specimen.getStorageContainer().getId() == null && specimen.getStorageContainer().getName() == null))
		{
			String message = ApplicationProperties.getValue("specimen.storageContainer");
			throw new DAOException(ApplicationProperties.getValue("errors.invalid", message));
		}

		if (specimen.getStorageContainer() != null && specimen.getStorageContainer().getName() != null)
		{
			StorageContainer storageContainerObj = specimen.getStorageContainer();
			String sourceObjectName = StorageContainer.class.getName();
			String[] selectColumnName = {"id"};
			String[] whereColumnName = {"name"};
			String[] whereColumnCondition = {"="};
			Object[] whereColumnValue = {specimen.getStorageContainer().getName()};
			String joinCondition = null;

			List list = dao.retrieve(sourceObjectName, selectColumnName, whereColumnName, whereColumnCondition, whereColumnValue, joinCondition);

			if (!list.isEmpty())
			{
				storageContainerObj.setId((Long) list.get(0));
				specimen.setStorageContainer(storageContainerObj);
			}
			else
			{
				String message = ApplicationProperties.getValue("specimen.storageContainer");
				throw new DAOException(ApplicationProperties.getValue("errors.invalid", message));
			}
		}

		//Events Validation
		/**
		 * Name: Virender Mehta
		 * Reviewer name: Aarti Sharma
		 * Description: Resolved Performance Issue,retrive SpecimenEventCollection  
		 * 				In the case if Add Operation receive and collection object are associated with specimen object
		 * 				but in the case of Edit specimen event parameter is no longer associated with Specimen thus 
		 * 				retriving explicetely specimenEeventcollection. 
		 */
		Collection specimenEventCollection = null;
		if (specimen.getId() != null)
		{
			specimenEventCollection = dao.retrieve(SpecimenEventParameters.class.getName(), "specimen.id", specimen.getId());
		}
		else
		{
			specimenEventCollection = specimen.getSpecimenEventCollection();
		}
		if (specimenEventCollection != null && !specimenEventCollection.isEmpty())
		{
			Iterator specimenEventCollectionIterator = specimenEventCollection.iterator();
			while (specimenEventCollectionIterator.hasNext())
			{
				Object eventObject = specimenEventCollectionIterator.next();
				EventsUtil.validateEventsObject(eventObject, validator);
			}
		}
		else
		{
			throw new DAOException(ApplicationProperties.getValue("error.specimen.noevents"));
		}

		//Validations for Biohazard Add-More Block
		Collection bioHazardCollection = specimen.getBiohazardCollection();
		Biohazard biohazard = null;
		if (bioHazardCollection != null && !bioHazardCollection.isEmpty())
		{
			Iterator itr = bioHazardCollection.iterator();
			while (itr.hasNext())
			{
				biohazard = (Biohazard) itr.next();
				if (!validator.isValidOption(biohazard.getType()))
				{
					String message = ApplicationProperties.getValue("newSpecimen.msg");
					throw new DAOException(ApplicationProperties.getValue("errors.newSpecimen.biohazard.missing", message));
				}
				if (biohazard.getId() == null)
				{
					String message = ApplicationProperties.getValue("newSpecimen.msg");
					throw new DAOException(ApplicationProperties.getValue("errors.newSpecimen.biohazard.missing", message));
				}
			}
		}

		//validations for external identifiers
		Collection extIdentifierCollection = specimen.getExternalIdentifierCollection();
		ExternalIdentifier extIdentifier = null;
		if (extIdentifierCollection != null && !extIdentifierCollection.isEmpty())
		{
			Iterator itr = extIdentifierCollection.iterator();
			while (itr.hasNext())
			{
				extIdentifier = (ExternalIdentifier) itr.next();
				if (validator.isEmpty(extIdentifier.getName()))
				{
					String message = ApplicationProperties.getValue("specimen.msg");
					throw new DAOException(ApplicationProperties.getValue("errors.specimen.externalIdentifier.missing", message));
				}
				if (validator.isEmpty(extIdentifier.getValue()))
				{
					String message = ApplicationProperties.getValue("specimen.msg");
					throw new DAOException(ApplicationProperties.getValue("errors.specimen.externalIdentifier.missing", message));
				}
			}
		}
		//End Ashish

		if (Constants.ALIQUOT.equals(specimen.getLineage()))
		{
			//return true;
		}

		validateFields(specimen, dao, operation, partOfMulipleSpecimen);

		List specimenClassList = CDEManager.getCDEManager().getPermissibleValueList(Constants.CDE_NAME_SPECIMEN_CLASS, null);
		String specimenClass = Utility.getSpecimenClassName(specimen);

		if (!Validator.isEnumeratedValue(specimenClassList, specimenClass))
		{
			throw new DAOException(ApplicationProperties.getValue("protocol.class.errMsg"));
		}

		if (!Validator.isEnumeratedValue(Utility.getSpecimenTypes(specimenClass), specimen.getType()))
		{
			throw new DAOException(ApplicationProperties.getValue("protocol.type.errMsg"));
		}

		SpecimenCharacteristics characters = specimen.getSpecimenCharacteristics();

		if (characters == null)
		{
			throw new DAOException(ApplicationProperties.getValue("specimen.characteristics.errMsg"));
		}
		else
		{
			if (specimen.getSpecimenCollectionGroup() != null)
			{
				//				NameValueBean undefinedVal = new NameValueBean(Constants.UNDEFINED,Constants.UNDEFINED);
				List tissueSiteList = CDEManager.getCDEManager().getPermissibleValueList(Constants.CDE_NAME_TISSUE_SITE, null);

				if (!Validator.isEnumeratedValue(tissueSiteList, characters.getTissueSite()))
				{
					throw new DAOException(ApplicationProperties.getValue("protocol.tissueSite.errMsg"));
				}

				//		    	NameValueBean unknownVal = new NameValueBean(Constants.UNKNOWN,Constants.UNKNOWN);
				List tissueSideList = CDEManager.getCDEManager().getPermissibleValueList(Constants.CDE_NAME_TISSUE_SIDE, null);

				if (!Validator.isEnumeratedValue(tissueSideList, characters.getTissueSide()))
				{
					throw new DAOException(ApplicationProperties.getValue("specimen.tissueSide.errMsg"));
				}

				List pathologicalStatusList = CDEManager.getCDEManager().getPermissibleValueList(Constants.CDE_NAME_PATHOLOGICAL_STATUS, null);

				if (!Validator.isEnumeratedValue(pathologicalStatusList, specimen.getPathologicalStatus()))
				{
					throw new DAOException(ApplicationProperties.getValue("protocol.pathologyStatus.errMsg"));
				}
			}
		}

		if (operation.equals(Constants.EDIT))
		{
			if (specimen.getCollectionStatus() != null && specimen.getCollectionStatus().equals("Collected")
					&& !specimen.getAvailable().booleanValue())
			{
				throw new DAOException(ApplicationProperties.getValue("specimen.available.operation"));
			}
		}

		if (operation.equals(Constants.ADD))
		{
			if (!specimen.getAvailable().booleanValue())
			{
				throw new DAOException(ApplicationProperties.getValue("specimen.available.errMsg"));
			}

			if (!Constants.ACTIVITY_STATUS_ACTIVE.equals(specimen.getActivityStatus()))
			{
				throw new DAOException(ApplicationProperties.getValue("activityStatus.active.errMsg"));
			}
		}
		else
		{
			if (!Validator.isEnumeratedValue(Constants.ACTIVITY_STATUS_VALUES, specimen.getActivityStatus()))
			{
				throw new DAOException(ApplicationProperties.getValue("activityStatus.errMsg"));
			}
		}
		//Logger.out.debug("End-Inside validate method of specimen bizlogic");

		//Validating createdOn date
		if (specimen.getCreatedOn() != null && specimen.getLineage() != null && !specimen.getLineage().equalsIgnoreCase(Constants.NEW_SPECIMEN))
		{
			String tempDate = Utility.parseDateToString(specimen.getCreatedOn(), Constants.DATE_PATTERN_MM_DD_YYYY);
			if (!validator.checkDate(tempDate))
			{
				throw new DAOException(ApplicationProperties.getValue("error.invalid.createdOnDate"));
			}
		}
		return true;
	}

	private void validateFields(Specimen specimen, DAO dao, String operation, boolean partOfMulipleSpecimen) throws DAOException
	{
		Validator validator = new Validator();

		if (partOfMulipleSpecimen)
		{

			if (specimen.getSpecimenCollectionGroup() == null || validator.isEmpty(specimen.getSpecimenCollectionGroup().getGroupName()))
			{
				String quantityString = ApplicationProperties.getValue("specimen.specimenCollectionGroup");
				throw new DAOException(ApplicationProperties.getValue("errors.item.required", quantityString));
			}

			List spgList = dao
					.retrieve(SpecimenCollectionGroup.class.getName(), Constants.NAME, specimen.getSpecimenCollectionGroup().getGroupName());

			if (spgList.size() == 0)
			{
				throw new DAOException(ApplicationProperties.getValue("errors.item.unknown", "Specimen Collection Group "
						+ specimen.getSpecimenCollectionGroup().getGroupName()));
			}
		}

		//		if (validator.isEmpty(specimen.getLabel()))
		//		{
		//			String labelString = ApplicationProperties.getValue("specimen.label");
		//			throw new DAOException(ApplicationProperties.getValue("errors.item.required", labelString));
		//		}

		if (specimen.getInitialQuantity() == null || specimen.getInitialQuantity().getValue() == null)
		{
			String quantityString = ApplicationProperties.getValue("specimen.quantity");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", quantityString));
		}

		if (specimen.getAvailableQuantity() == null || specimen.getAvailableQuantity().getValue() == null)
		{
			String quantityString = ApplicationProperties.getValue("specimen.availableQuantity");
			throw new DAOException(ApplicationProperties.getValue("errors.item.required", quantityString));
		}

		/**
		 * This method gives first valid storage position to a specimen if it is not given. 
		 * If storage position is given it validates the storage position 
		 **/
		StorageContainerUtil.validateStorageLocationForSpecimen(specimen);

	}

	/**
	 * validates multiple specimen. Internally it for each specimen it innvokes validateSingleSpecimen. 
	 * @throws DAOException
	 * @throws DAOException
	 private boolean validateMultipleSpecimen(Map specimenMap, DAO dao, String operation) throws DAOException
	 {

	 populateStorageLocations(dao, specimenMap);
	 Iterator specimenIterator = specimenMap.keySet().iterator();
	 boolean result = true;
	 while (specimenIterator.hasNext() && result == true)
	 {
	 Specimen specimen = (Specimen) specimenIterator.next();
	 //validate single specimen
	 */

	/*	if (specimenCollectionGroup != null)
	 {

	 if (specimenCollectionGroup.getActivityStatus().equals(Constants.ACTIVITY_STATUS_CLOSED))
	 {
	 throw new DAOException("Specimen Collection Group " + ApplicationProperties.getValue("error.object.closed"));
	 }
	 
	 specimen.setSpecimenCollectionGroup(specimenCollectionGroup);
	 }  
	 List spgList = dao.retrieve(SpecimenCollectionGroup.class.getName(), Constants.NAME, specimen.getSpecimenCollectionGroup().getName());
	 if(spgList!=null && !spgList.isEmpty())
	 {
	 specimenCollectionGroup = (SpecimenCollectionGroup) spgList.get(0);
	 }
	 
	 else if(specimen.getParentSpecimen()!=null)
	 {
	 List spList = dao.retrieve(Specimen.class.getName(), Constants.SYSTEM_LABEL, specimen.getParentSpecimen().getLabel());
	 if (spList != null && !spList.isEmpty())
	 {
	 Specimen sp = (Specimen) spList.get(0);
	 specimenCollectionGroup = sp.getSpecimenCollectionGroup();
	 }
	 
	 } */
	/*
	 // TODO uncomment code for label, performance
	 
	 try
	 {
	 result = validateSingleSpecimen(specimen, dao, operation, true);
	 }
	 catch (DAOException daoException)
	 {
	 String message = daoException.getMessage();
	 message += " (This message is for Specimen number " + specimen.getId() + ")";
	 daoException.setMessage(message);
	 throw daoException;
	 }

	 List derivedSpecimens = (List) specimenMap.get(specimen);

	 if (derivedSpecimens == null)
	 {
	 continue;
	 }

	 //validate derived specimens
	 for (int i = 0; i < derivedSpecimens.size(); i++)
	 {

	 Specimen derivedSpecimen = (Specimen) derivedSpecimens.get(i);
	 derivedSpecimen.setSpecimenCharacteristics(specimen.getSpecimenCharacteristics());
	 derivedSpecimen.setSpecimenCollectionGroup(specimen.getSpecimenCollectionGroup());
	 derivedSpecimen.setPathologicalStatus(specimen.getPathologicalStatus());

	 try
	 {
	 result = validateSingleSpecimen(derivedSpecimen, dao, operation, false);
	 }
	 catch (DAOException daoException)
	 {
	 int j = i + 1;
	 String message = daoException.getMessage();
	 message += " (This message is for Derived Specimen " + j + " of Parent Specimen number " + specimen.getId() + ")";
	 daoException.setMessage(message);
	 throw daoException;
	 }

	 if (!result)
	 {
	 break;
	 }
	 }

	 }
	 return result;
	 }
	 */
	/**
	 * 
	 *  Start --> Code added for auto populating storage locations in case of multiple specimen
	 */

	/**
	 * This method populates SCG Id and storage locations for Multiple Specimen
	 * @param dao
	 * @param specimenMap
	 * @throws DAOException
	 private void populateStorageLocations(DAO dao, Map specimenMap) throws DAOException
	 {
	 final String saperator = "$";
	 Map tempSpecimenMap = new HashMap();
	 Iterator specimenIterator = specimenMap.keySet().iterator();
	 while (specimenIterator.hasNext())
	 {
	 Specimen specimen = (Specimen) specimenIterator.next();
	 //validate single specimen
	 if (specimen.getSpecimenCollectionGroup() != null)
	 {
	 String[] selectColumnName = {"collectionProtocolRegistration.id"};
	 String[] whereColumnName = {Constants.NAME};
	 String[] whereColumnCondition = {"="};
	 String[] whereColumnValue = {specimen.getSpecimenCollectionGroup().getName()};
	 List spCollGroupList = dao.retrieve(SpecimenCollectionGroup.class.getName(), selectColumnName, whereColumnName, whereColumnCondition,
	 whereColumnValue, null);
	 // TODO saperate calls for SCG - ID and cpid
	 // SCG - ID will be needed before populateStorageLocations
	 
	 // TODO test
	 if (!spCollGroupList.isEmpty())
	 {
	 //Object idList[] = (Object[]) spCollGroupList.get(0); // Move up + here
	 long cpId = ((Long) spCollGroupList.get(0)).longValue();
	 //Long scgId = (Long) idList[0]; // Move up 
	 //long cpId = ((Long) idList[0]).longValue();//here
	 //specimen.getSpecimenCollectionGroup().setId(scgId); // Move up
	 List tempListOfSpecimen = (ArrayList) tempSpecimenMap.get(cpId + saperator + specimen.getClassName());
	 if (tempListOfSpecimen == null)
	 {
	 tempListOfSpecimen = new ArrayList();
	 }
	 int i = 0;
	 for (; i < tempListOfSpecimen.size(); i++)
	 {
	 Specimen sp = (Specimen) tempListOfSpecimen.get(i);
	 
	 if ((sp.getId() != null) && (specimen.getId().longValue() < sp.getId().longValue()))
	 break;
	 }
	 tempListOfSpecimen.add(i, specimen);
	 tempSpecimenMap.put(cpId + saperator + specimen.getClassName(), tempListOfSpecimen);
	 
	 List listOfDerivedSpecimen = (ArrayList) specimenMap.get(specimen);
	 // TODO
	 if (listOfDerivedSpecimen != null)
	 {
	 for (int j = 0; j < listOfDerivedSpecimen.size(); j++)
	 {
	 Specimen tempDerivedSpecimen = (Specimen) listOfDerivedSpecimen.get(j);
	 String derivedKey = cpId + saperator + tempDerivedSpecimen.getClassName();
	 List listOfSpecimen = (ArrayList) tempSpecimenMap.get(derivedKey);
	 if (listOfSpecimen == null)
	 {
	 listOfSpecimen = new ArrayList();
	 }
	 listOfSpecimen.add(tempDerivedSpecimen);
	 tempSpecimenMap.put(derivedKey, listOfSpecimen);
	 }
	 }
	 }
	 }
	 }

	 
	 Iterator keyIterator = tempSpecimenMap.keySet().iterator();
	 while (keyIterator.hasNext())
	 {
	 String key = (String) keyIterator.next();
	 StorageContainerBizLogic scbizLogic = (StorageContainerBizLogic) BizLogicFactory.getInstance().getBizLogic(
	 Constants.STORAGE_CONTAINER_FORM_ID);
	 String split[] = key.split("[$]");
	 // TODO when moved to acion pass true
	 TreeMap containerMap = scbizLogic.getAllocatedContaienrMapForSpecimen((Long.parseLong(split[0])), split[1], 0, "", false);
	 List listOfSpecimens = (ArrayList) tempSpecimenMap.get(key);
	 allocatePositionToSpecimensList(specimenMap, listOfSpecimens, containerMap);
	 }
	 }
	 */

	/**
	 * This function gets the default positions for list of specimens
	 * @param specimenMap
	 * @param listOfSpecimens
	 * @param containerMap
	 private void allocatePositionToSpecimensList(Map specimenMap, List listOfSpecimens, Map containerMap)
	 {
	 List newListOfSpecimen = new ArrayList();
	 */
	/*	for (int i = 0; i < listOfSpecimens.size(); i++)
	 {
	 Specimen tempSpecimen = (Specimen) listOfSpecimens.get(i);
	 newListOfSpecimen.add(tempSpecimen);
	 List listOfDerivedSpecimen = (ArrayList) specimenMap.get(tempSpecimen);
	 // TODO
	 if (listOfDerivedSpecimen != null)
	 {
	 for (int j = 0; j < listOfDerivedSpecimen.size(); j++)
	 {
	 Specimen tempDerivedSpecimen = (Specimen) listOfDerivedSpecimen.get(j);
	 newListOfSpecimen.add(tempDerivedSpecimen);
	 }
	 }
	 }
	 */
	/*
	 Iterator iterator = containerMap.keySet().iterator();
	 int i = 0;
	 while (iterator.hasNext())
	 {
	 NameValueBean nvb = (NameValueBean) iterator.next();
	 Map tempMap = (Map) containerMap.get(nvb);
	 if (tempMap.size() > 0)
	 {
	 boolean result = false;
	 for (; i < newListOfSpecimen.size(); i++)
	 {
	 Specimen tempSpecimen = (Specimen) newListOfSpecimen.get(i);
	 result = allocatePositionToSingleSpecimen(specimenMap, tempSpecimen, tempMap, nvb);
	 if (result == false) // container is exhausted
	 break;
	 }
	 if (result == true)
	 break;
	 }
	 }
	 }
	 */
	/**
	 *  This function gets the default position specimen,the position should not be used by any other specimen in specimenMap
	 *  This is required because we might have given the same position to another specimen.
	 * @param specimenMap
	 * @param tempSpecimen
	 * @param tempMap
	 * @param nvb
	 * @return
	 
	 private boolean allocatePositionToSingleSpecimen(Map specimenMap, Specimen tempSpecimen, Map tempMap, NameValueBean nvbForContainer)
	 {
	 Iterator itr = tempMap.keySet().iterator();
	 String containerId = nvbForContainer.getValue(), xPos, yPos;
	 while (itr.hasNext())
	 {
	 NameValueBean nvb = (NameValueBean) itr.next();
	 xPos = nvb.getValue();

	 List list = (List) tempMap.get(nvb);
	 for (int i = 0; i < list.size(); i++)
	 {
	 nvb = (NameValueBean) list.get(i);
	 yPos = nvb.getValue();
	 boolean result = checkPositionValidForSpecimen(containerId, xPos, yPos, specimenMap);
	 if (result == true)
	 {
	 StorageContainer tempStorageContainer = new StorageContainer();
	 tempStorageContainer.setId(new Long(Long.parseLong(containerId)));
	 tempSpecimen.setPositionDimensionOne(new Integer(Integer.parseInt(xPos)));
	 tempSpecimen.setPositionDimensionTwo(new Integer(Integer.parseInt(yPos)));
	 tempSpecimen.setStorageContainer(tempStorageContainer);
	 return true;
	 }
	 }

	 }
	 return false;
	 }
	 */
	/**
	 * This method checks whether the given parameters match with parameters in specimen Map
	 * @param containerId
	 * @param pos
	 * @param pos2
	 * @param specimenMap
	 * @return

	 private boolean checkPositionValidForSpecimen(String containerId, String xpos, String ypos, Map specimenMap)
	 {

	 // TODO can be optimised by passing list		
	 Iterator specimenIterator = specimenMap.keySet().iterator();
	 while (specimenIterator.hasNext())
	 {
	 Specimen specimen = (Specimen) specimenIterator.next();
	 boolean matchFound = checkMatchingPosition(containerId, xpos, ypos, specimen);
	 if (matchFound == true)
	 return false;

	 List derivedSpecimens = (List) specimenMap.get(specimen);

	 if (derivedSpecimens != null)
	 {
	 for (int i = 0; i < derivedSpecimens.size(); i++)
	 {

	 Specimen derivedSpecimen = (Specimen) derivedSpecimens.get(i);
	 matchFound = checkMatchingPosition(containerId, xpos, ypos, derivedSpecimen);
	 if (matchFound == true)
	 return false;
	 }
	 }
	 }
	 return true;
	 }
	 */
	/**
	 * This method checks whether the given parameters match with parameters of the specimen 
	 * @param containerId
	 * @param pos
	 * @param pos2
	 * @param specimen
	 * @return

	 private boolean checkMatchingPosition(String containerId, String xpos, String ypos, Specimen specimen)
	 {
	 String storageContainerId = "";
	 if (specimen.getStorageContainer() != null && specimen.getStorageContainer().getId() != null)
	 storageContainerId += specimen.getStorageContainer().getId();
	 else
	 return false;

	 String pos1 = specimen.getPositionDimensionOne() + "";
	 String pos2 = specimen.getPositionDimensionTwo() + "";
	 if (storageContainerId.equals(containerId) && xpos.equals(pos1) && ypos.equals(pos2))
	 return true;
	 return false;
	 }
	 */

	/**
	 * 
	 *  End --> Code added for auto populating storage locations in case of multiple specimen
	 */

	/* This function checks whether the storage position of a specimen is changed or not 
	 * & returns the status accordingly.
	 */
	private boolean isStoragePositionChanged(Specimen oldSpecimen, Specimen newSpecimen)
	{
		StorageContainer oldContainer = oldSpecimen.getStorageContainer();
		StorageContainer newContainer = newSpecimen.getStorageContainer();

		//Added for api: Jitendra
		if ((oldContainer == null && newContainer != null) || (oldContainer != null && newContainer == null))
		{
			return true;
		}

		if (oldContainer != null && newContainer != null)
		{
			if (oldContainer.getId().longValue() == newContainer.getId().longValue())
			{
				if (oldSpecimen.getPositionDimensionOne().intValue() == newSpecimen.getPositionDimensionOne().intValue())
				{
					if (oldSpecimen.getPositionDimensionTwo().intValue() == newSpecimen.getPositionDimensionTwo().intValue())
					{
						return false;
					}
					else
					{
						return true;
					}
				}
				else
				{
					return true;
				}
			}
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}

	}

	public String getPageToShow()
	{
		return new String();
	}

	public List getMatchingObjects()
	{
		return new ArrayList();
	}

	// added by Ashwin for bug id# 2476 
	/**
	 * Set event parameters from parent specimen to derived specimen
	 * @param parentSpecimen specimen
	 * @return set
	 */
	private Set populateDeriveSpecimenEventCollection(Specimen parentSpecimen, Specimen deriveSpecimen)
	{
		Set deriveEventCollection = new HashSet();
		Set parentSpecimeneventCollection = (Set) parentSpecimen.getSpecimenEventCollection();
		SpecimenEventParameters specimenEventParameters = null;
		SpecimenEventParameters deriveSpecimenEventParameters = null;

		try
		{
			if (parentSpecimeneventCollection != null)
			{
				for (Iterator iter = parentSpecimeneventCollection.iterator(); iter.hasNext();)
				{
					specimenEventParameters = (SpecimenEventParameters) iter.next();
					deriveSpecimenEventParameters = (SpecimenEventParameters) specimenEventParameters.clone();
					deriveSpecimenEventParameters.setId(null);
					deriveSpecimenEventParameters.setSpecimen(deriveSpecimen);
					deriveEventCollection.add(deriveSpecimenEventParameters);
				}
			}
		}
		catch (CloneNotSupportedException exception)
		{
			exception.printStackTrace();
		}

		return deriveEventCollection;
	}

	/**
	 * This method will retrive no of specimen in the catissue_specimen table.
	 * @return Total No of Specimen
	 * @throws ClassNotFoundException
	 * @throws DAOException 
	 */
	public int totalNoOfSpecimen(SessionDataBean sessionData)
	{
		String sql = "select MAX(IDENTIFIER) from CATISSUE_SPECIMEN";
		JDBCDAO jdbcDao = (JDBCDAO) DAOFactory.getInstance().getDAO(Constants.JDBC_DAO);
		int noOfRecords = 0;
		try
		{
			jdbcDao.openSession(sessionData);
			List resultList = jdbcDao.executeQuery(sql, sessionData, false, null);
			String number = (String) ((List) resultList.get(0)).get(0);
			if (number == null || number.equals(""))
			{
				number = "0";
			}
			noOfRecords = Integer.parseInt(number);
			jdbcDao.closeSession();
		}
		catch (DAOException daoexception)
		{
			daoexception.printStackTrace();
		}
		catch (ClassNotFoundException classnotfound)
		{
			classnotfound.printStackTrace();
		}
		return noOfRecords;
	}

	/**
	 * Name: Virender Mehta
	 * Reviewer: sachin lale
	 * This function will retrive SCG Id from SCG Name
	 * @param specimen
	 * @param dao
	 * @throws DAOException
	 */
	public void retriveSCGIdFromSCGName(Specimen specimen, DAO dao) throws DAOException
	{
		String specimenCollGpName = specimen.getSpecimenCollectionGroup().getGroupName();
		if (specimenCollGpName != null && !specimenCollGpName.equals(""))
		{
			String[] selectColumnName = {"id"};
			String[] whereColumnName = {"name"};
			String[] whereColumnCondition = {"="};
			String[] whereColumnValue = {specimenCollGpName};
			List scgList = dao.retrieve(SpecimenCollectionGroup.class.getName(), selectColumnName, whereColumnName, whereColumnCondition,
					whereColumnValue, null);
			if (scgList != null && !scgList.isEmpty())
			{
				specimen.getSpecimenCollectionGroup().setId(((Long) scgList.get(0)));
			}
		}
	}

	//Mandar: 16-Jan-07 ConsentWithdrawl
	/*
	 * This method updates the consents and specimen based on the the consent withdrawal option.
	 */
	private void updateConsentWithdrawStatus(Specimen specimen, Specimen oldSpecimen, DAO dao, SessionDataBean sessionDataBean) throws DAOException
	{
		if (!specimen.getConsentWithdrawalOption().equalsIgnoreCase(Constants.WITHDRAW_RESPONSE_NOACTION))
		{

			String consentWithdrawOption = specimen.getConsentWithdrawalOption();
			//Resolved Lazy ----  specimen.getConsentTierStatusCollection()
			Collection consentTierStatusCollection = specimen.getConsentTierStatusCollection();
			//Collection consentTierStatusCollection = (Collection)dao.retrieveAttribute(Specimen.class.getName(), specimen.getId(),"elements(consentTierStatusCollection)");
			Iterator itr = consentTierStatusCollection.iterator();
			while (itr.hasNext())
			{
				ConsentTierStatus status = (ConsentTierStatus) itr.next();
				long consentTierID = status.getConsentTier().getId().longValue();
				if (status.getStatus().equalsIgnoreCase(Constants.WITHDRAWN))
				{
					//Resolved lazy - specimen.getChildrenSpecimen();

					WithdrawConsentUtil.updateSpecimenStatus(specimen, consentWithdrawOption, consentTierID, dao, sessionDataBean);

				}
			}
		}
	}

	/*
	 * This method is used to update the consent staus of child specimens as per the option selected by the user.
	 */
	private void updateConsentStatus(Specimen specimen, DAO dao, Specimen oldSpecimen) throws DAOException
	{
		if (!specimen.getApplyChangesTo().equalsIgnoreCase(Constants.APPLY_NONE))
		{
			String applyChangesTo = specimen.getApplyChangesTo();
			Collection consentTierStatusCollection = specimen.getConsentTierStatusCollection();
			Collection oldConsentTierStatusCollection = oldSpecimen.getConsentTierStatusCollection();
			Iterator itr = consentTierStatusCollection.iterator();
			while (itr.hasNext())
			{
				ConsentTierStatus status = (ConsentTierStatus) itr.next();
				long consentTierID = status.getConsentTier().getId().longValue();
				String statusValue = status.getStatus();
				//Collection childSpecimens = oldSpecimen.getChildrenSpecimen();
				Collection childSpecimens = (Collection) dao.retrieveAttribute(Specimen.class.getName(), specimen.getId(),
						"elements(childrenSpecimen)");
				Iterator childItr = childSpecimens.iterator();
				while (childItr.hasNext())
				{
					Specimen childSpecimen = (Specimen) childItr.next();
					WithdrawConsentUtil.updateSpecimenConsentStatus(childSpecimen, applyChangesTo, consentTierID, statusValue,
							consentTierStatusCollection, oldConsentTierStatusCollection, dao);
				}
			}
		}
	}

	/**
	 * This function is used to update specimens and their dervied & aliquot 
	 * specimens. 
	 * @param newSpecimenCollection List of specimens to update along with children 
	 * specimens.
	 * @param sessionDataBean current user session information
	 * @throws DAOException If DAO fails to update one or more specimens
	 * this function will throw DAOException.
	 */
	public void updateAnticipatorySpecimens(Collection newSpecimenCollection, SessionDataBean sessionDataBean) throws DAOException
	{
		updateMultipleSpecimens(newSpecimenCollection, sessionDataBean, true);
	}

	/**
	 * This function is used to bulk update multiple specimens. If
	 * any specimen contains children specimens those will be inserted. 
	 * @param newSpecimenCollection List of specimens to update along with 
	 * new children specimens if any. 7
	 * @param sessionDataBean current user session information
	 * @throws DAOException If DAO fails to update one or more specimens
	 * this function will throw DAOException.

	 */
	public void bulkUpdateSpecimens(Collection newSpecimenCollection, SessionDataBean sessionDataBean) throws DAOException
	{
		Iterator iterator = newSpecimenCollection.iterator();
		DAO dao = DAOFactory.getInstance().getDAO(Constants.HIBERNATE_DAO);
		int specimenCtr = 1;
		int childSpecimenCtr = 0;
		try
		{

			((HibernateDAO) dao).openSession(sessionDataBean);
			while (iterator.hasNext())
			{
				Specimen newSpecimen = (Specimen) iterator.next();
				if(newSpecimen.getStorageContainer() != null && newSpecimen.getStorageContainer().getId() == null)
					setStorageContainerId(dao, newSpecimen, newSpecimen.getStorageContainer());
			}
			iterator = newSpecimenCollection.iterator();

			while (iterator.hasNext())
			{
				Specimen newSpecimen = (Specimen) iterator.next();
					
				Specimen specimenDO = updateSignleSpecimen(dao, newSpecimen, sessionDataBean, false);

				Collection childrenSpecimenCollection = newSpecimen.getChildrenSpecimen();
				if (childrenSpecimenCollection != null && !childrenSpecimenCollection.isEmpty())
				{
					Iterator childIterator = childrenSpecimenCollection.iterator();
					while (childIterator.hasNext())
					{
						childSpecimenCtr++;
						Specimen childSpecimen = (Specimen) childIterator.next();
						childSpecimen.setParentSpecimen(specimenDO);
						insertSingleSpecimen(childSpecimen, dao, sessionDataBean, false);

					}
					childSpecimenCtr = 0;
				}
				specimenCtr++;
			}
			specimenCtr = 0;
			((HibernateDAO) dao).commit();
			postInsert(newSpecimenCollection, dao, sessionDataBean);

		}
		catch (Exception exception)
		{
			((AbstractDAO) dao).rollback();
			String errorMsg = "Failed to save. ";
			if (specimenCtr != 0)
			{
				errorMsg = "specimen number " + specimenCtr + " cannot be saved. ";
				if (childSpecimenCtr != 0)
				{
					errorMsg = "Cannot insert child specimen " + childSpecimenCtr + ", of specimen " + specimenCtr + ". ";
				}

			}
			throw new DAOException(errorMsg + exception.getMessage());
		}
		finally
		{

			((HibernateDAO) dao).closeSession();
		}

	}

	protected void updateMultipleSpecimens(Collection newSpecimenCollection, SessionDataBean sessionDataBean, boolean updateChildrens)
			throws DAOException
	{
		Iterator iterator = newSpecimenCollection.iterator();
		AbstractDAO dao = DAOFactory.getInstance().getDAO(Constants.HIBERNATE_DAO);
		try
		{

			dao.openSession(sessionDataBean);
			
			//kalpana bug#6001
			while (iterator.hasNext())
			{
				Specimen newSpecimen = (Specimen) iterator.next();
				if (newSpecimen.getPositionDimensionOne() != null || 
						newSpecimen.getPositionDimensionTwo() != null)
				{
					
					String storageValue = newSpecimen.getStorageContainer().getName()+":"+newSpecimen.getPositionDimensionOne()+" ,"+ 
					newSpecimen.getPositionDimensionTwo();				
					storageContainerIds.add(storageValue);
					
				}
				if(newSpecimen.getChildrenSpecimen()!=null)
				{
					allocatePositionForChildrenSpecimen(newSpecimen.getChildrenSpecimen());
				}
				
			}
			
			iterator = newSpecimenCollection.iterator();
			while (iterator.hasNext())
			{
				Specimen newSpecimen = (Specimen) iterator.next();
				setStorageLocationToNewSpecimen(dao, newSpecimen, sessionDataBean, true);
			}
			iterator = newSpecimenCollection.iterator();
			storageContainerIds.clear();
			while (iterator.hasNext())
			{
				Specimen newSpecimen = (Specimen) iterator.next();
				updateSignleSpecimen(dao, newSpecimen, sessionDataBean, updateChildrens);
			}
			dao.commit();
			postInsert(newSpecimenCollection, dao, sessionDataBean);

		}
		catch (Exception exception)
		{
			dao.rollback();
			throw new DAOException("Failed to update multiple specimen " + exception.getMessage());
		}
		finally
		{

			((HibernateDAO) dao).closeSession();
		}
	}

	public Specimen updateSignleSpecimen(DAO dao, Specimen newSpecimen, SessionDataBean sessionDataBean, boolean updateChildrens) throws DAOException
	{
		try
		{
			List specList = dao.retrieve(Specimen.class.getName(), "id", newSpecimen.getId());
			if (specList != null && !specList.isEmpty())
			{
				Specimen specimenDO = (Specimen) specList.get(0);
				updateSpecimenDomainObject(dao, newSpecimen, specimenDO);
				if (updateChildrens)
					updateChildrenSpecimens(dao, newSpecimen, specimenDO);

				dao.update(specimenDO, sessionDataBean, false, false, false);
				return specimenDO;
			}
			else
			{
				throw new DAOException("Invalid Specimen with label" + newSpecimen.getLabel());
			}
		}
		catch (UserNotAuthorizedException authorizedException)
		{
			throw new DAOException("User not authorized to update specimens" + authorizedException.getMessage());

		}
		catch (SMException exception)
		{
			throw new DAOException(exception.getMessage(), exception);
		}
	}

	private void updateChildrenSpecimens(DAO dao, Specimen specimenVO, Specimen specimenDO) throws DAOException, SMException
	{
		Collection childrenSpecimens = specimenDO.getChildrenSpecimen();
		if (childrenSpecimens == null || childrenSpecimens.isEmpty())
		{
			return;
		}
		Iterator iterator = childrenSpecimens.iterator();
		while (iterator.hasNext())
		{
			Specimen specimen = (Specimen) iterator.next();
			Specimen relatedSpecimen = getCorelatedSpecimen(specimen.getId(), specimenVO.getChildrenSpecimen());
			if (relatedSpecimen != null)
			{
				updateSpecimenDomainObject(dao, relatedSpecimen, specimen);

				updateChildrenSpecimens(dao, relatedSpecimen, specimen);
			}
		}
	}

	private Specimen getCorelatedSpecimen(Long id, Collection specimenCollection) throws DAOException
	{
		Iterator iterator = specimenCollection.iterator();
		while (iterator.hasNext())
		{
			Specimen specimen = (Specimen) iterator.next();
			if (specimen.getId().longValue() == id.longValue())
			{
				return specimen;
			}
		}
		return null;
	}

	private void checkDuplicateSpecimenFields(Specimen specimen, DAO dao) throws DAOException
	{
		List list = dao.retrieve(Specimen.class.getCanonicalName(), "label", specimen.getLabel());
		if (!list.isEmpty())
		{
			for (int i = 0; i < list.size(); i++)
			{
				Specimen specimenObject = (Specimen) (list.get(i));
				if (!specimenObject.getId().equals(specimen.getId()))
				{
					throw new DAOException("Label " + specimen.getLabel() + " is already exists!");

				}
			}
		}
		if (specimen.getBarcode() != null)
		{
			list = dao.retrieve(Specimen.class.getCanonicalName(), "barcode", specimen.getBarcode());
			if (!list.isEmpty())
			{
				for (int i = 0; i < list.size(); i++)
				{
					Specimen specimenObject = (Specimen) (list.get(i));
					if (!specimenObject.getId().equals(specimen.getId()))
					{
						throw new DAOException("Barcode " + specimen.getBarcode() + " is already exists.");

					}
				}
			}
		}
	}

	private void updateSpecimenDomainObject(DAO dao, Specimen specimenVO, Specimen specimenDO) throws DAOException, SMException
	{

		if (specimenVO.getBarcode() != null && specimenVO.getBarcode().trim().length() == 0)
		{
			specimenVO.setBarcode(null);
		}
		checkDuplicateSpecimenFields(specimenVO, dao);
		specimenDO.setLabel(specimenVO.getLabel());
		specimenDO.setBarcode(specimenVO.getBarcode());
		specimenDO.setAvailable(specimenVO.getAvailable());

		if (specimenVO.getStorageContainer() != null)
		{
			setStorageContainer(dao, specimenVO, specimenDO);
		}
		else
		{
			specimenDO.setStorageContainer(null);
		}

		if (specimenVO.getInitialQuantity() != null)
		{
			Quantity quantity = specimenVO.getInitialQuantity();
			Quantity availableQuantity = specimenVO.getAvailableQuantity();
			Double quantityValue = quantity.getValue();
			Double availableQuantityValue = availableQuantity.getValue();
			if (specimenDO.getInitialQuantity() == null)
			{
				quantity = new Quantity();
				specimenDO.setInitialQuantity(quantity);
			}
			else
			{
				quantity = specimenDO.getInitialQuantity();
			}

			if (specimenDO.getAvailableQuantity() == null)
			{
				quantity = new Quantity();
				specimenDO.setAvailableQuantity(quantity);
			}
			else
			{
				/**
				 * Name: Abhishek Mehta 
				 * Bug ID: 5558
				 * Patch ID: 5558_3
				 * See also: 1-3 
				 * Description : Earlier the available quantity for specimens that haven't been collected yet is greater than 0.
				 */
				if ((specimenDO.getAvailableQuantity().getValue().doubleValue() == 0 && Constants.COLLECTION_STATUS_COLLECTED.equalsIgnoreCase(
						specimenVO.getCollectionStatus())))
				{
					specimenDO.setAvailableQuantity(specimenVO.getInitialQuantity());
				}
				else
				{
					availableQuantity = specimenDO.getAvailableQuantity();
					availableQuantity.setValue(availableQuantityValue);
				}
			}
			
			quantity.setValue(quantityValue);
		}
		if (specimenVO.getCollectionStatus() != null)
		{
			specimenDO.setCollectionStatus(specimenVO.getCollectionStatus());
		}

		// code for multiple specimen edit
		if (specimenVO.getCreatedOn() != null)
		{
			specimenDO.setCreatedOn(specimenVO.getCreatedOn());
		}
		if (specimenVO.getPathologicalStatus() != null)
		{
			specimenDO.setPathologicalStatus(specimenVO.getPathologicalStatus());
		}

		if (specimenVO.getSpecimenCharacteristics() != null)
		{
			SpecimenCharacteristics characteristics = specimenVO.getSpecimenCharacteristics();
			if (characteristics.getTissueSide() != null || characteristics.getTissueSite() != null)
			{
				SpecimenCharacteristics specimenCharacteristics = specimenDO.getSpecimenCharacteristics();
				if (specimenCharacteristics != null)
				{
					specimenCharacteristics.setTissueSide(specimenVO.getSpecimenCharacteristics().getTissueSide());
					specimenCharacteristics.setTissueSite(specimenVO.getSpecimenCharacteristics().getTissueSite());
				}
			}
		}

		if (specimenVO.getComment() != null)
		{
			specimenDO.setComment(specimenVO.getComment());
		}
		if (specimenVO.getBiohazardCollection() != null && !specimenVO.getBiohazardCollection().isEmpty())
		{
			specimenDO.setBiohazardCollection(specimenVO.getBiohazardCollection());
		}

		if (Constants.MOLECULAR.equals(specimenVO.getClassName()))
		{
			Double concentration = ((MolecularSpecimen) specimenVO).getConcentrationInMicrogramPerMicroliter();

			((MolecularSpecimen) specimenDO).setConcentrationInMicrogramPerMicroliter(concentration);
		}

	}

	public Map<Long, Collection> getContainerHoldsCPs()
	{
		return containerHoldsCPs;
	}

	public void setContainerHoldsCPs(Map<Long, Collection> containerHoldsCPs)
	{
		this.containerHoldsCPs = containerHoldsCPs;
	}

	public Map<Long, Collection> getContainerHoldsSpecimenClasses()
	{
		return containerHoldsSpecimenClasses;
	}

	public void setContainerHoldsSpecimenClasses(Map<Long, Collection> containerHoldsSpecimenClasses)
	{
		this.containerHoldsSpecimenClasses = containerHoldsSpecimenClasses;
	}

	/**
	 * @param dao
	 * @param specimenVO
	 * @param specimenDO
	 * @throws DAOException
	 */
	private void setStorageContainer(DAO dao, Specimen specimenVO, Specimen specimenDO) throws DAOException, SMException
	{
		StorageContainer storageContainer = specimenVO.getStorageContainer();

		specimenDO.setPositionDimensionOne(specimenVO.getPositionDimensionOne());
		specimenDO.setPositionDimensionTwo(specimenVO.getPositionDimensionTwo());
		specimenDO.setStorageContainer(storageContainer);
	}

	/**
	 * @param dao
	 * @param storageContainer
	 * @param containerId
	 * @return
	 * @throws DAOException
	 */
	private StorageContainer retrieveStorageContainerObject(DAO dao, StorageContainer storageContainer, Long containerId) throws DAOException
	{
		List storageContainerList;
		if (containerId != null)
		{
			storageContainerList = dao.retrieve(StorageContainer.class.getName(), "id", containerId);
		}
		else
		{
			storageContainerList = dao.retrieve(StorageContainer.class.getName(), "name", storageContainer.getName());
		}
		if (storageContainerList == null || storageContainerList.isEmpty())
		{
			throw new DAOException("Container name is invalid");
		}
		storageContainer = (StorageContainer) storageContainerList.get(0);
		return storageContainer;
	}

	public boolean isCpbased()
	{
		return cpbased;
	}

	public void setCpbased(boolean cpbased)
	{
		this.cpbased = cpbased;
	}

	/**
	 * This function throws BizLogicException if the domainObj is of type SpecimenCollectionRequirementGroup
	 * @param domainObj
	 * @param uiForm
	 */

	protected void prePopulateUIBean(AbstractDomainObject domainObj, IValueObject uiForm) throws BizLogicException
	{

		Specimen specimen = (Specimen) domainObj;
		AbstractSpecimenCollectionGroup absspecimenCollectionGroup = specimen.getSpecimenCollectionGroup();
		Object proxySpecimenCollectionGroup = HibernateMetaData.getProxyObjectImpl(absspecimenCollectionGroup);
		if ((proxySpecimenCollectionGroup instanceof SpecimenCollectionRequirementGroup))
		{
			NewSpecimenForm newSpecimenForm = (NewSpecimenForm) uiForm;
			newSpecimenForm.setForwardTo(Constants.PAGEOF_SPECIMEN_COLLECTION_REQUIREMENT_GROUP);
			throw new BizLogicException("The Specimen is Added as Requirement, this can not be edited!!");

		}

	}

}