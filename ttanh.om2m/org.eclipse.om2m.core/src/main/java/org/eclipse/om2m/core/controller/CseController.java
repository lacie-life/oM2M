/*******************************************************************************
 * Copyright (c) 2013-2015 LAAS-CNRS (www.laas.fr)
 * 7 Colonel Roche 31077 Toulouse - France
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Thierry Monteil (Project co-founder) - Management and initial specification,
 *         conception and documentation.
 *     Mahdi Ben Alaya (Project co-founder) - Management and initial specification,
 *         conception, implementation, test and documentation.
 *     Christophe Chassot - Management and initial specification.
 *     Khalil Drira - Management and initial specification.
 *     Yassine Banouar - Initial specification, conception, implementation, test
 *         and documentation.
 *     Guillaume Garzone - Conception, implementation, test and documentation.
 *     Francois Aissaoui - Conception, implementation, test and documentation.
 ******************************************************************************/
package org.eclipse.om2m.core.controller;

import java.util.Date;

import javax.persistence.EntityManager;

import org.eclipse.om2m.commons.resource.ErrorInfo;
import org.eclipse.om2m.commons.resource.OnlineStatus;
import org.eclipse.om2m.commons.resource.Refs;
import org.eclipse.om2m.commons.resource.Cse;
import org.eclipse.om2m.commons.resource.StatusCode;
import org.eclipse.om2m.commons.rest.RequestIndication;
import org.eclipse.om2m.commons.rest.ResponseConfirm;
import org.eclipse.om2m.commons.utils.DateConverter;
import org.eclipse.om2m.commons.utils.XmlMapper;
import org.eclipse.om2m.core.constants.Constants;
import org.eclipse.om2m.core.dao.DAOFactory;
import org.eclipse.om2m.core.dao.DBAccess;
import org.eclipse.om2m.core.notifier.Notifier;

/**
 * Implements Create, Retrieve, Update, Delete and Execute methods to handle
 * generic REST request for {@link Scl} resource.
 *
 * @author <ul>
 *         <li>Yassine Banouar < ybanouar@laas.fr > < yassine.banouar@gmail.com ></li>
 *         <li>Mahdi Ben Alaya < ben.alaya@laas.fr > < benalaya.mahdi@gmail.com ></li>
 *         </ul>
 */

public class CseController extends Controller {

	/**
	 * Creates {@link Scl} resource.
	 * @param requestIndication - The generic request to handle.
	 * @return The generic returned response.
	 */
	public ResponseConfirm doCreate (RequestIndication requestIndication) {

		// containersReference:             (createReq NP) (response M)
		// groupsReference:                 (createReq NP) (response M)
		// applicationsReference:           (createReq NP) (response M)
		// accessRightsReference:           (createReq NP) (response M)
		// subscriptionsReference:          (createReq NP) (response M)
		// mgmtObjsReference:               (createReq NP) (response M)
		// notificationChannelsReference:   (createReq NP) (response M)
		// m2mpocsReference:                (createReq NP) (response M)
		// attachedDevicesReference:        (createReq NP) (response M)
		// sclId:                           (createReq M)  (response M)
		// pocs:                            (createReq O)  (response M)
		// remTriggerAddr:                  (createReq O)  (response O)
		// onlineStatus:                    (createReq NP) (response M)
		// serverCapability:                (createReq NP) (response M)
		// link:                            (createReq M)  (response M)
		// schedule:                        (createReq O)  (response O)
		// expirationTime:                  (createReq O)  (response M)
		// accessRightID:                   (createReq O)  (response O)
		// searchStrings:                   (createReq O)  (response M)
		// creationTime:                    (createReq NP) (response M)
		// lastModifiedTime:                (createReq NP) (response M)
		// locTargetDevice:                 (createReq O)  (response O)
		// mgmtProtocolType:                (createReq M)  (response M)
		// integrityValResult:              (createReq O)  (response O)
		// aPocHandling:                    (createReq NP) (response O)

		ResponseConfirm errorResponse = new ResponseConfirm();
		EntityManager em = DBAccess.createEntityManager();
		em.getTransaction().begin();

		// Check AccessRight
		String accessRightID = getAccessRightId(requestIndication.getTargetID(), em);

		// CheckResourceParentExistence
		if (accessRightID == null) {
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist")) ;
		}

		errorResponse = checkAccessRight(accessRightID, requestIndication.getRequestingEntity(), Constants.AR_CREATE);
		if (errorResponse != null) {
			em.close();
			return errorResponse;
		}
		// Check Resource Representation
		if (requestIndication.getRepresentation() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Resource Representation is EMPTY")) ;
		}

		// Checks on attributes
		Cse cse = null ; 
		try{
			cse = (Cse) XmlMapper.getInstance().xmlToObject(requestIndication.getRepresentation());
		} catch (ClassCastException e){
			em.close();
			LOGGER.debug("ClassCastException : Incorrect resource type in JAXB unmarshalling.",e);
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource type"));
		}
		if (cse == null){
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource representation syntax")) ;
		}
		// SclId is Mandatory
		if (cse.getCseId() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST," cseId is Mandatory")) ;
		} else if (!cse.getCseId().matches(Constants.ID_REGEXPR)) { // Check Id conformity
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"cseId should match the following regexpr: " + Constants.ID_REGEXPR)) ;
		}
		// Check the Id uniqueness
		if (DAOFactory.getCseDAO().find(requestIndication.getTargetID()+"/"+cse.getCseId(), em) != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_CONFLICT,"CseId Conflit")) ;
		}
		// Check ExpirationTime
		if (cse.getExpirationTime() != null && !checkExpirationTime(cse.getExpirationTime())) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Expiration Time CREATE is Out of Date")) ;
		}
		// CreationTime Must be NP
		if (cse.getCreationTime() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Creation Time CREATE is Not Permitted")) ;
		}
		// LastModifiedTime Must be NP
		if (cse.getLastModifiedTime() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Last Modified Time CREATE is Not Permitted")) ;
		}
		// Containers Reference Must be NP
		if (cse.getContainersReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Containers Reference is not permitted")) ;
		}
		//Groups Reference Must be NP
		if (cse.getGroupsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Groups Reference is not permitted")) ;
		}
		// Applications Reference Must be NP
		if (cse.getApplicationsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Applications Reference is not permitted")) ;
		}
		// AccessRights Reference Must be NP
		if (cse.getAccessRightsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"AccessRights Reference is not permitted")) ;
		}
		// Subscriptions Reference Must be NP
		if (cse.getSubscriptionsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Subscriptions Reference is not permitted")) ;
		}
		// MgmtObjs Reference Must be NP
		if (cse.getMgmtObjsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"MgmtObjs Reference is not permitted")) ;
		}
		// NotificationChannels Reference Must be NP
		if (cse.getNotificationChannelsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"NotificationChannels Reference is not permitted")) ;
		}
		// m2mPocs Reference Must be NP
		if (cse.getM2MPocsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"M2MPocs Reference is not permitted")) ;
		}
		// AttachedDevices Reference Must be NP
		if (cse.getAttachedDevicesReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"AttachedDevices Reference is not permitted")) ;
		}
		// OnlineStatus attribute Must be NP
		if (cse.getOnlineStatus() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"OnLineStatus is not permitted")) ;
		}
		// ServerCapability Attribute Must be NP
		if (cse.isServerCapability() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"ServerCapability is not permitted")) ;
		}
		// Link attribute Must be M
		if (cse.getLink() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Link attribute is mandatory")) ;
		}
		// MgmtProtocolType attribute Must be M
		if (cse.getMgmtProtocolType() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"MgmtProtocol attribute is mandatory")) ;
		}
		// aPocHandling attribute Must be NP
		if (cse.getAPocHandling() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"aPocHandling is not permitted")) ;
		}

		// Storage
		// Set URI
		cse.setUri(requestIndication.getTargetID()+ "/" +cse.getCseId());
		// Set Expiration Time if it is null
		if (cse.getExpirationTime() == null) {
			// ExpirationTime infinity value
			cse.setExpirationTime(getNewExpirationTime(Constants.EXPIRATION_TIME));
		}
		// Set AccessRightID from the Parent if it's null or nonexistent
		if (DAOFactory.getAccessRightDAO().find(cse.getAccessRightID(), em) == null) {
			cse.setAccessRightID(accessRightID);
		}
		// Set searchString if it's null
		if (cse.getSearchStrings() == null) {
			cse.setSearchStrings(generateSearchStrings(cse.getClass().getSimpleName(), cse.getCseId()));
		}
		// Mandatory Attributes
		// OnlineStatus
		cse.setOnlineStatus(OnlineStatus.ONLINE);
		cse.setServerCapability(true);
		// Set References
		cse.setContainersReference(cse.getUri()+Refs.CONTAINERS_REF);
		cse.setGroupsReference(cse.getUri()+Refs.GROUPS_REF);
		cse.setApplicationsReference(cse.getUri()+Refs.APPLICATIONS_REF);
		cse.setAccessRightsReference(cse.getUri()+Refs.ACCESSRIGHTS_REF);
		cse.setSubscriptionsReference(cse.getUri()+Refs.SUBSCRIPTIONS_REF);
		cse.setMgmtObjsReference(cse.getUri()+Refs.MGMTOBJS_REF);
		cse.setNotificationChannelsReference(cse.getUri()+Refs.NOTIFICATIONCHANNELS_REF);
		cse.setM2MPocsReference(cse.getUri()+"/m2mPocs");
		cse.setAttachedDevicesReference(cse.getUri()+Refs.ATTACHEDDEVICES_REF);
		// Set CreationTime
		cse.setCreationTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());
		// Set LastModifiedTime
		cse.setLastModifiedTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());

		// Notify the subscribers
		Notifier.notify(StatusCode.STATUS_CREATED, cse);

		// Store scl
		DAOFactory.getCseDAO().create(cse, em);
		em.getTransaction().commit();
		em.close();
		
		// Response
		return new ResponseConfirm(StatusCode.STATUS_CREATED,cse);
	}

	/**
	 * Retrieves {@link Scl} resource.
	 * @param requestIndication - The generic request to handle.
	 * @return The generic returned response.
	 */
	public ResponseConfirm doRetrieve (RequestIndication requestIndication) {

		// containersReference:             (response M)
		// groupsReference:                 (response M)
		// applicationsReference:           (response M)
		// accessRightsReference:           (response M)
		// subscriptionsReference:          (response M)
		// mgmtObjsReference:               (response M)
		// notificationChannelsReference:   (response M)
		// m2mpocsReference:                (response M)
		// attachedDevicesReference:        (response M)
		// sclId:                           (response M)
		// pocs:                            (response M)
		// remTriggerAddr:                  (response O)
		// onlineStatus:                    (response M)
		// serverCapability:                (response M)
		// link:                            (response M)
		// schedule:                        (response O)
		// expirationTime:                  (response M)
		// accessRightID:                   (response O)
		// searchStrings:                   (response M)
		// creationTime:                    (response M)
		// lastModifiedTime:                (response M)
		// locTargetDevice:                 (response O)
		// mgmtProtocolType:                (response M)
		// integrityValResult:              (response O)
		// aPocHandling:                    (response O)

		ResponseConfirm errorResponse = new ResponseConfirm();
		
		EntityManager em = DBAccess.createEntityManager();
		em.getTransaction().begin();
		Cse cse = DAOFactory.getCseDAO().find(requestIndication.getTargetID(), em);
		em.close();
		// Check resource existence
		if (cse == null) {
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist in DataBase")) ;
		}
		// Check AccessRight
		errorResponse = checkAccessRight(cse.getAccessRightID(), requestIndication.getRequestingEntity(), Constants.AR_READ);
		if (errorResponse != null) {
			return errorResponse;
		}

		cse.setContainersReference(cse.getUri()+Refs.CONTAINERS_REF);
		cse.setGroupsReference(cse.getUri()+Refs.GROUPS_REF);
		cse.setApplicationsReference(cse.getUri()+Refs.APPLICATIONS_REF);
		cse.setAccessRightsReference(cse.getUri()+Refs.ACCESSRIGHTS_REF);
		cse.setSubscriptionsReference(cse.getUri()+Refs.SUBSCRIPTIONS_REF);
		cse.setMgmtObjsReference(cse.getUri()+Refs.MGMTOBJS_REF);
		cse.setNotificationChannelsReference(cse.getUri()+Refs.NOTIFICATIONCHANNELS_REF);
		cse.setM2MPocsReference(cse.getUri()+"/m2mPocs");
		cse.setAttachedDevicesReference(cse.getUri()+Refs.ATTACHEDDEVICES_REF);	

		// Response
		return new ResponseConfirm(StatusCode.STATUS_OK, cse);

	}

	/**
	 * Updates {@link Scl} resource.
	 * @param requestIndication - The generic request to handle.
	 * @return The generic returned response.
	 */
	public ResponseConfirm doUpdate (RequestIndication requestIndication) {

		// containersReference:             (updateReq NP) (response M)
		// groupsReference:                 (updateReq NP) (response M)
		// applicationsReference:           (updateReq NP) (response M)
		// accessRightsReference:           (updateReq NP) (response M)
		// subscriptionsReference:          (updateReq NP) (response M)
		// mgmtObjsReference:               (updateReq NP) (response M)
		// notificationChannelsReference:   (updateReq NP) (response M)
		// m2mpocsReference:                (updateReq NP) (response M)
		// attachedDevicesReference:        (updateReq NP) (response M)
		// sclId:                           (updateReq NP) (response M)
		// pocs:                            (updateReq O)  (response M)
		// remTriggerAddr:                  (updateReq O)  (response O)
		// onlineStatus:                    (updateReq NP) (response M)
		// serverCapability:                (updateReq NP) (response M)
		// link:                            (updateReq NP) (response M)
		// schedule:                        (updateReq O)  (response O)
		// expirationTime:                  (updateReq O)  (response M)
		// accessRightID:                   (updateReq O)  (response O)
		// searchStrings:                   (updateReq O)  (response M)
		// creationTime:                    (updateReq NP) (response M)
		// lastModifiedTime:                (updateReq NP) (response M)
		// locTargetDevice:                 (updateReq O)  (response O)
		// mgmtProtocolType:                (updateReq M)  (response M)
		// integrityValResult:              (updateReq O)  (response O)
		// aPocHandling:                    (updateReq NP) (response O)

		ResponseConfirm errorResponse = new ResponseConfirm();
		EntityManager em = DBAccess.createEntityManager();
		em.getTransaction().begin();
		Cse cse = DAOFactory.getCseDAO().find(requestIndication.getTargetID(), em);

		// Check Existence
		if (cse == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist in DataBase")) ;
		}
		// Check AccessRight
		errorResponse = checkAccessRight(cse.getAccessRightID(), requestIndication.getRequestingEntity(), Constants.AR_WRITE);
		if (errorResponse != null) {
			em.close();
			return errorResponse;
		}
		// Check Resource Representation
		if (requestIndication.getRepresentation() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Resource Representation is EMPTY")) ;
		}
		// Checks attributes
		// Construct New Resource
		Cse cseNew = null ; 
		try{
			cseNew = (Cse) XmlMapper.getInstance().xmlToObject(requestIndication.getRepresentation());
		} catch (ClassCastException e){
			em.close();
			LOGGER.debug("ClassCastException : Incorrect resource type in JAXB unmarshalling.",e);
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource type"));
		}
		if (cseNew == null){
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource representation syntax")) ;
		}
		// The Update of the SclId is NP
		if (cseNew.getCseId() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"CseId UPDATE is Not Permitted")) ;
		}
		// Check ExpirationTime
		if (cseNew.getExpirationTime() != null && !checkExpirationTime(cseNew.getExpirationTime())) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Expiration Time UPDATE is Out of Date")) ;
		}
		// Containers Reference Must be NP
		if (cseNew.getContainersReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Containers Reference UPDATE is Not Permitted")) ;
		}
		// Groups Reference Must be NP
		if (cseNew.getGroupsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Groups Reference UPDATE is Not Permitted")) ;
		}
		// Applications Reference Must be NP
		if (cseNew.getApplicationsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Applications Reference UPDATE is Not Permitted")) ;
		}
		// AccessRights Reference Must be NP
		if (cseNew.getAccessRightsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"AccessRights Reference UPDATE is Not Permitted")) ;
		}
		// Subscriptions Reference Must be NP
		if (cseNew.getSubscriptionsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Subscriptions Reference UPDATE is Not Permitted")) ;
		}
		// MgmtObjs Reference Must be NP
		if (cseNew.getMgmtObjsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"MgmtObjs Reference UPDATE is Not Permitted")) ;
		}
		// NotificationChannels Reference Must be NP
		if (cseNew.getNotificationChannelsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"NotificationChannels Reference UPDATE is Not Permitted")) ;
		}
		// m2mPocs Reference Must be NP
		if (cseNew.getM2MPocsReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"M2MPocs Reference UPDATE is Not Permitted")) ;
		}
		// AttachedDevices Reference Must be NP
		if (cseNew.getAttachedDevicesReference() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"AttachedDevices Reference UPDATE is Not Permitted")) ;
		}
		// OnlineStatus attribute Must be NP
		if (cseNew.getOnlineStatus() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"OnLineStatus attribute UPDATE is Not Permitted")) ;
		}
		// ServerCapability Attribute Must be NP
		if (cseNew.isServerCapability() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"ServerCapability attrivute UPDATE is Not Permitted")) ;
		}

		// Link Attribute Must be NP
		if (cseNew.getLink() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Link attribute UPDATE is Not Permitted")) ;
		}
		// MgmtProtocolType is Mandatory
		if (cseNew.getMgmtProtocolType() == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"MgmtProtocolType attrivute UPDATE is Mandatory")) ;
		}
		// CreationTime Must be NP
		if (cseNew.getCreationTime() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"CreationTime UPDATE is Not Permitted")) ;
		}
		// LastModifiedTime Must be NP
		if (cseNew.getLastModifiedTime() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"LatModifiedTime UPDATE is Not Permitted")) ;
		}
		// aPocHandling Must be NP
		if (cseNew.getAPocHandling() != null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"aPocHandling attribute UPDATE is Not Permitted")) ;
		}
		// Storage
		// Set Expiration Time
		if (cseNew.getExpirationTime() != null) {
			cse.setExpirationTime(cseNew.getExpirationTime());
		}
		// Set accessRightID if it exists
		if (DAOFactory.getAccessRightDAO().find(cseNew.getAccessRightID(), em) != null) {
			cse.setAccessRightID(cseNew.getAccessRightID());
		}
		// Set searchStrings from New Scl if it exists
		if (cseNew.getSearchStrings() != null) {
			cse.setSearchStrings(cseNew.getSearchStrings());
		}
		// Set pocs from New Scl if it exists
		if (cseNew.getPocs() != null) {
			cse.setPocs(cseNew.getPocs());
		}
		// Set remTriggerAddr from New Scl if it exists
		if (cseNew.getRemTriggerAddr() != null) {
			cse.setRemTriggerAddr(cseNew.getRemTriggerAddr());
		}
		// Set schedule from New Scl if it exists
		if (cseNew.getSchedule() != null) {
			cse.setSchedule(cseNew.getSchedule());
		}
		// Set locTargetDevice from New Scl if it exists
		if (cseNew.getLocTargetDevice() != null) {
			cse.setLocTargetDevice(cseNew.getLocTargetDevice());
		}
		// Set mgmtProtocolType from New Scl if it exists
		cse.setMgmtProtocolType(cseNew.getMgmtProtocolType());
		// Set integrityValResults from New Scl if it exists
		if (cseNew.getIntegrityValResults() != null) {
			cse.setIntegrityValResults(cseNew.getIntegrityValResults());
		}
		// Set LastModifiedTime
		cse.setLastModifiedTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());

		// Notify the subscribers
		Notifier.notify(StatusCode.STATUS_OK, cse);
		
		// Store scl
		DAOFactory.getCseDAO().update(cse, em);
		em.getTransaction().commit();
		em.close();
		
		// Set references
		cse.setContainersReference(cse.getUri()+Refs.CONTAINERS_REF);
		cse.setGroupsReference(cse.getUri()+Refs.GROUPS_REF);
		cse.setApplicationsReference(cse.getUri()+Refs.APPLICATIONS_REF);
		cse.setAccessRightsReference(cse.getUri()+Refs.ACCESSRIGHTS_REF);
		cse.setSubscriptionsReference(cse.getUri()+Refs.SUBSCRIPTIONS_REF);
		cse.setMgmtObjsReference(cse.getUri()+Refs.MGMTOBJS_REF);
		cse.setNotificationChannelsReference(cse.getUri()+Refs.NOTIFICATIONCHANNELS_REF);
		cse.setM2MPocsReference(cse.getUri()+ Refs.M2MPOCS_REF);
		cse.setAttachedDevicesReference(cse.getUri()+Refs.ATTACHEDDEVICES_REF);
		
		// Response
		return new ResponseConfirm(StatusCode.STATUS_OK, cse);
	}

	/**
	 * Deletes {@link Scl} resource.
	 * @param requestIndication - The generic request to handle.
	 * @return The generic returned response.
	 */
	public ResponseConfirm doDelete (RequestIndication requestIndication) {

		ResponseConfirm errorResponse = new ResponseConfirm();
		EntityManager em = DBAccess.createEntityManager();
		em.getTransaction().begin();
		Cse cse = DAOFactory.getCseDAO().find(requestIndication.getTargetID(), em);

		// Check Resource Existence
		if (cse == null) {
			em.close();
			return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist")) ;
		}
		// Check AccessRight
		errorResponse = checkAccessRight(cse.getAccessRightID(), requestIndication.getRequestingEntity(), Constants.AR_DELETE);
		if (errorResponse != null) {
			em.close();
			return errorResponse;
		}

		// Notify the subscribers
		Notifier.notify(StatusCode.STATUS_DELETED, cse);
		//Delete
		DAOFactory.getCseDAO().delete(cse,em);
		em.getTransaction().commit();
		em.close();
		// Response
		return new ResponseConfirm(StatusCode.STATUS_OK);

	}

	/**
	 * Executes {@link Scl} resource.
	 * @param requestIndication - The generic request to handle.
	 * @return The generic returned response.
	 */
	public ResponseConfirm doExecute (RequestIndication requestIndication) {

		// Response
		return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_IMPLEMENTED,requestIndication.getMethod()+" Method is not implemented")) ;
	}

}
