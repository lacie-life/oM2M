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

import org.eclipse.om2m.commons.resource.AccessRight;
import org.eclipse.om2m.commons.resource.AnnounceTo;
import org.eclipse.om2m.commons.resource.ErrorInfo;
import org.eclipse.om2m.commons.resource.Refs;
import org.eclipse.om2m.commons.resource.StatusCode;
import org.eclipse.om2m.commons.rest.RequestIndication;
import org.eclipse.om2m.commons.rest.ResponseConfirm;
import org.eclipse.om2m.commons.utils.DateConverter;
import org.eclipse.om2m.commons.utils.XmlMapper;
import org.eclipse.om2m.core.announcer.Announcer;
import org.eclipse.om2m.core.constants.Constants;
import org.eclipse.om2m.core.dao.DAOFactory;
import org.eclipse.om2m.core.dao.DBAccess;
import org.eclipse.om2m.core.notifier.Notifier;

/**
 * Implements Create, Retrieve, Update, Delete and Execute methods to handle
 * generic REST request for {@link AccessRight} resource.
 *
 * @author <ul>
 *         <li>Yassine Banouar < ybanouar@laas.fr > < yassine.banouar@gmail.com ></li>
 *         <li>Mahdi Ben Alaya < ben.alaya@laas.fr > < benalaya.mahdi@gmail.com ></li>
 *         </ul>
 */

public class AccessRightController extends Controller {

    /**
     * Creates {@link AccessRight} resource.
     * @param requestIndication - The generic request to handle.
     * @return The generic returned response.
     */
    public ResponseConfirm doCreate (RequestIndication requestIndication) {

        // subscriptionsReference:  (createReq NP) (response M)
        // expirationTime:          (createReq O)  (response M*)
        // searchStrings:           (createReq O)  (response M)
        // creationTime:            (createReq NP) (response M)
        // lastModifiedTime:        (createReq NP) (response M)
        // announceTo:              (createReq O)  (response M*)
        // permissions:             (createReq O)  (response M)
        // selfPermissions:         (createReq M)  (response M)
        // id:                      (createReq O)  (response M*)

        ResponseConfirm errorResponse = new ResponseConfirm();
        
        EntityManager em = DBAccess.createEntityManager();
        em.getTransaction().begin();
        
        String accessRightID = this.getAccessRightId(requestIndication.getTargetID(), em);
        
        // Check Parent Resource Existence
        if (accessRightID == null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist")) ;
        }
        // Check AccessRight
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
        // Generate the Object from the XML Representation
        AccessRight accessRight = null ; 
        try{
        	accessRight = (AccessRight) XmlMapper.getInstance().xmlToObject(requestIndication.getRepresentation());
        } catch (ClassCastException e){
        	em.close();
        	LOGGER.debug("ClassCastException : Incorrect resource type in JAXB unmarshalling.",e);
        	return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource type"));
        }
        if (accessRight == null){
        	em.close();
        	return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource representation syntax")) ;
        }
        
        // Checks on Attributes
        // Check ID Conformity
        if (accessRight.getId() != null && !accessRight.getId().matches(Constants.ID_REGEXPR)){
        	em.close();
        	return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Id should match the following regexpr: " + Constants.ID_REGEXPR));
        }
        // Check the Id uniqueness
        if (accessRight.getId() != null && DAOFactory.getAccessRightDAO().find(requestIndication.getTargetID()+"/"+accessRight.getId(), em) != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_CONFLICT,"Access Right Id Conflit")) ;
        }
        // Generate the Id if does not exist
        if (accessRight.getId() == null || accessRight.getId().isEmpty()) {
            accessRight.setId(generateId("AR_",""));
        }
        // Check ExpirationTime
        if (accessRight.getExpirationTime() != null && !checkExpirationTime(accessRight.getExpirationTime())) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Expiration Time is out of Date")) ;
        }
        // SubscriptionsReference Must be NP
        if (accessRight.getSubscriptionsReference() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Subscriptions Reference is not Permitted")) ;
        }
        // CreationTime Must be NP
        if (accessRight.getCreationTime() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Creation Time is not Permitted")) ;
        }
        // LastModifiedTime Must be NP
        if (accessRight.getLastModifiedTime() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Last Modified Time is Not Permitted")) ;
        }
        // selfPermissions attribute is Mandatory
        if (accessRight.getSelfPermissions().getPermission() == null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"selfPermissions attribute is Mandatory")) ;
        }
        // Storage
        // Set URI
        accessRight.setUri(requestIndication.getTargetID()+"/"+accessRight.getId());
        // Set Expiration Time if it's null
        if (accessRight.getExpirationTime() == null) {
            // Expiration default value
            accessRight.setExpirationTime(getNewExpirationTime(Constants.EXPIRATION_TIME));
        }
        // Set searchString if it does not exist
        if (accessRight.getSearchStrings() == null) {
            accessRight.setSearchStrings(generateSearchStrings(accessRight.getClass().getSimpleName(), accessRight.getId()));
        }
        // Set announceTo if it is null
        if (accessRight.getAnnounceTo() == null) {
            AnnounceTo announceTo = new AnnounceTo();
            announceTo.setActivated(false);
            announceTo.setGlobal(false);
            accessRight.setAnnounceTo(announceTo);
        }
        // Set References
        accessRight.setSubscriptionsReference(accessRight.getUri()+Refs.SUBSCRIPTIONS_REF);
        // Set CreationTime
        accessRight.setCreationTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());
        // Set LastModifiedTime
        accessRight.setLastModifiedTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());

        // Announcement
        if (accessRight.getAnnounceTo().isActivated()) {
            accessRight.setAnnounceTo(new Announcer().announce(accessRight.getAnnounceTo(), accessRight.getUri(), accessRight.getSearchStrings(), requestIndication.getRequestingEntity()));
        }

        // Notify the subscribers
        Notifier.notify(StatusCode.STATUS_CREATED, accessRight);

        // Store accessRight
        DAOFactory.getAccessRightDAO().create(accessRight, em);
        
        em.getTransaction().commit();
        em.close();
        
        // Response
        return new ResponseConfirm(StatusCode.STATUS_CREATED, accessRight);
    }

    /**
     * Retrieves {@link AccessRight} resource.
     * @param requestIndication - The generic request to handle.
     * @return The generic returned response.
     */
    public ResponseConfirm doRetrieve (RequestIndication requestIndication) {

        // subscriptionsReference:  (response M)
        // expirationTime:          (response M*)
        // searchStrings:           (response M)
        // creationTime:            (response M)
        // lastModifiedTime:        (response M)
        // announceTo:              (response M*)
        // permissions:             (response M)
        // selfPermissions:         (response M)
        // id:                      (response M*)

        ResponseConfirm errorResponse = new ResponseConfirm();
        
        EntityManager em = DBAccess.createEntityManager();
        em.getTransaction().begin();
        AccessRight accessRight = DAOFactory.getAccessRightDAO().find(requestIndication.getTargetID(), em);
        em.close();

        // Check resource existence
        if (accessRight == null) {
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist in DataBase")) ;
        }
        // Check AccessRight Based on self Permissions
        errorResponse = checkSelfPermissions(accessRight.getSelfPermissions(), requestIndication.getRequestingEntity(), Constants.AR_READ);
        if (errorResponse != null) {
            return errorResponse;
        }
        
		accessRight.setSubscriptionsReference(accessRight.getUri()+Refs.SUBSCRIPTIONS_REF);
        
		// Response
        return new ResponseConfirm(StatusCode.STATUS_OK, accessRight);
    }

    /**
     * Updates {@link AccessRight} resource.
     * @param requestIndication - The generic request to handle.
     * @return The generic returned response.
     */
    public ResponseConfirm doUpdate (RequestIndication requestIndication) {

        // subscriptionsReference:  (updateReq NP) (response M)
        // expirationTime:          (updateReq O)  (response M*)
        // searchStrings:           (updateReq O)  (response M)
        // creationTime:            (updateReq NP) (response M)
        // lastModifiedTime:        (updateReq NP) (response M)
        // announceTo:              (updateReq O)  (response M*)
        // permissions:             (updateReq O)  (response M)
        // selfPermissions:         (updateReq M)  (response M)
        // id:                      (updateReq NP) (response M*)

        ResponseConfirm errorResponse = new ResponseConfirm();
        EntityManager em = DBAccess.createEntityManager();
        em.getTransaction().begin();
        AccessRight accessRight = DAOFactory.getAccessRightDAO().find(requestIndication.getTargetID(), em);

        // Check Existence
        if (accessRight == null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist in DataBase")) ;
        }
        // Check AccessRight Based on self Permissions
        errorResponse =  checkSelfPermissions(accessRight.getSelfPermissions(), requestIndication.getRequestingEntity(), Constants.AR_WRITE);
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
        AccessRight accessRightNew = null ; 
        try{
        	accessRightNew = (AccessRight) XmlMapper.getInstance().xmlToObject(requestIndication.getRepresentation());
        } catch (ClassCastException e){
        	em.close();
        	LOGGER.debug("ClassCastException : Incorrect resource type in JAXB unmarshalling.",e);
        	return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource type"));
        }
        if (accessRightNew == null){
        	em.close();
        	return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST, "Incorrect resource representation syntax")) ;
        }
        // The Update of the accessRightId is NP
        if (accessRightNew.getId() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"accessRightId is not Permitted")) ;
        }
        // Check ExpirationTime
        if (accessRightNew.getExpirationTime() != null && !checkExpirationTime(accessRightNew.getExpirationTime())) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Expiration Time is out of Date")) ;
        }
        // SubscriptionsReference Must be NP
        if (accessRightNew.getSubscriptionsReference() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Subscriptions Reference is not Permitted")) ;
        }
        // CreationTime Must be NP
        if (accessRightNew.getCreationTime() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Creation Time is not Permitted")) ;
        }
        // LastModifiedTime Must be NP
        if (accessRightNew.getLastModifiedTime() != null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"Last Modified Time is not Permitted")) ;
        }
        // selfPermissions is Mandatory
        if (accessRightNew.getSelfPermissions().getPermission() == null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_BAD_REQUEST,"selfPermissions attribute is Mandatory")) ;
        }
        // Storage
        // Set Expiration Time
        if (accessRightNew.getExpirationTime() != null) {
            accessRight.setExpirationTime(accessRightNew.getExpirationTime());
        }
        // Set SearchStrings if it exists
        if (accessRightNew.getSearchStrings()  != null) {
            accessRight.setSearchStrings(accessRightNew.getSearchStrings());
        }
        // Set announceTo
        if (accessRightNew.getAnnounceTo() != null) {
            accessRight.setAnnounceTo(accessRightNew.getAnnounceTo());
        }
        // Set Permissions
        if (accessRightNew.getPermissions() != null) {
            accessRight.setPermissions(accessRightNew.getPermissions());
        }
        // Set selfPermissions
        accessRight.setSelfPermissions(accessRightNew.getSelfPermissions());
        // Set LastModifiedTime
        accessRight.setLastModifiedTime(DateConverter.toXMLGregorianCalendar(new Date()).toString());
        // Notify the subscribers
        Notifier.notify(StatusCode.STATUS_OK, accessRight);

        // Store accessRight
        DAOFactory.getAccessRightDAO().update(accessRight, em);
        
        em.getTransaction().commit();
        em.close();

		accessRight.setSubscriptionsReference(accessRight.getUri()+Refs.SUBSCRIPTIONS_REF);
        
        // Response
        return new ResponseConfirm(StatusCode.STATUS_OK, accessRight);
    }

    /**
     * Deletes {@link AccessRight} resource.
     * @param requestIndication - The generic request to handle.
     * @return The generic returned response.
     */
    public ResponseConfirm doDelete (RequestIndication requestIndication) {

        ResponseConfirm errorResponse = new ResponseConfirm();
        EntityManager em = DBAccess.createEntityManager();
        em.getTransaction().begin();
        AccessRight accessRight = DAOFactory.getAccessRightDAO().find(requestIndication.getTargetID(), em);

        // Check Resource Existence
        if (accessRight == null) {
        	em.close();
            return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_FOUND,requestIndication.getTargetID()+" does not exist")) ;
        }
        // Check AccessRight Based on self Permissions
        errorResponse = checkSelfPermissions(accessRight.getSelfPermissions(), requestIndication.getRequestingEntity(), Constants.AR_DELETE);
        if (errorResponse != null) {
        	em.close();
            return errorResponse;
        }

        // De-announcement
        if (accessRight.getAnnounceTo().isActivated()) {
            new Announcer().deAnnounce(accessRight.getAnnounceTo(), accessRight.getUri(), requestIndication.getRequestingEntity());
        }

        // Notify the subscribers
        Notifier.notify(StatusCode.STATUS_DELETED, accessRight);

        // Delete
        DAOFactory.getAccessRightDAO().delete(accessRight, em);
        em.getTransaction().commit();
        em.close();
        
        // Response
        return new ResponseConfirm(StatusCode.STATUS_OK);
    }

    /**
     * Executes {@link AccessRight} resource.
     * @param requestIndication - The generic request to handle.
     * @return The generic returned response.
     */
    public ResponseConfirm doExecute (RequestIndication requestIndication) {

        //Response
        return new ResponseConfirm(new ErrorInfo(StatusCode.STATUS_NOT_IMPLEMENTED,requestIndication.getMethod()+" Method is not implmented"));
    }
}
