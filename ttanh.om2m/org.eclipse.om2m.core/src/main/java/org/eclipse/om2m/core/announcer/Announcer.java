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
 ******************************************************************************/
package org.eclipse.om2m.core.announcer;

import java.util.ArrayList;
import java.util.HashSet;

import javax.persistence.EntityManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.om2m.commons.resource.AccessRightAnnc;
import org.eclipse.om2m.commons.resource.AnnounceTo;
import org.eclipse.om2m.commons.resource.AnyURIList;
import org.eclipse.om2m.commons.resource.ApplicationAnnc;
import org.eclipse.om2m.commons.resource.ContainerAnnc;
import org.eclipse.om2m.commons.resource.GroupAnnc;
import org.eclipse.om2m.commons.resource.Refs;
import org.eclipse.om2m.commons.resource.Cse;
import org.eclipse.om2m.commons.resource.SearchStrings;
import org.eclipse.om2m.commons.rest.RequestIndication;
import org.eclipse.om2m.commons.rest.ResponseConfirm;
import org.eclipse.om2m.commons.utils.XmlMapper;
import org.eclipse.om2m.core.comm.RestClient;
import org.eclipse.om2m.core.constants.Constants;
import org.eclipse.om2m.core.dao.DAOFactory;
import org.eclipse.om2m.core.dao.DBAccess;

/**
 *Announces/De-Announces resources for which the announcement attribute is activated for each Creation/Delete.
 *
 * @author <ul>
 *         <li>Yassine Banouar < ybanouar@laas.fr > < yassine.banouar@gmail.com ></li>
 *         <li>Mahdi Ben Alaya < ben.alaya@laas.fr > < benalaya.mahdi@gmail.com ></li>
 *         </ul>
 */

public class Announcer {
    /** Logger */
    private static Log LOGGER = LogFactory.getLog(Announcer.class);

    /**
     * Announces the created resource.
     * @param announceTo - sclId target.
     * @param uri - resource uri.
     * @param requestingEntity - requesting entity
     * @return
     */
    public AnnounceTo announce(AnnounceTo announceTo, String uri, SearchStrings searchStrings, String requestingEntity) {
        // Checks if the sclList contains a remote sclReference.
        if (!announceTo.getCseList().getReference().isEmpty()) {
            String resourceId = uri.split("/")[uri.split("/").length - 1];
            String parentUri = uri.split("/"+resourceId)[0];
            String parentId = parentUri.split("/")[parentUri.split("/").length - 1];

            // Retrieve the scls from the SclList without redundancies
            final ArrayList<String> uniqueReferencesList = new ArrayList<String>(
                    new HashSet<String>(announceTo.getCseList().getReference()));
            final AnyURIList csenewList = new AnyURIList();
            final String decodedRequestingEntity = requestingEntity;

            String partialPath = null;
            String representation = null;

            // ApplicationAnnc
            if ("applications".equalsIgnoreCase(parentId)) {
                // scl/applications/App ==> distantScl/scls/scl/applications/AppAnnc
                ApplicationAnnc applicationAnnc = new ApplicationAnnc();
                applicationAnnc.setLink(uri);
                applicationAnnc.setId(resourceId+"Annc");
                applicationAnnc.setSearchStrings(searchStrings);
                representation = XmlMapper.getInstance().objectToXml(applicationAnnc);
                // Parent ApplicationAnnc partial path
                partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.APPLICATIONS_REF;
            }
            // ContainerAnnc
            if ("containers".equalsIgnoreCase(parentId)) {
                ContainerAnnc containerAnnc = new ContainerAnnc();
                containerAnnc.setLink(uri);
                containerAnnc.setId(resourceId + "Annc");
                containerAnnc.setSearchStrings(searchStrings);
                representation = XmlMapper.getInstance().objectToXml(containerAnnc);
                // Parent ContainerAnnc partial path
                if((Constants.CSE_ID+Refs.CONTAINERS_REF).equalsIgnoreCase(parentUri)) {
                    // scl/containers/Container ==> distantScl/scls/scl/containers
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.CONTAINERS_REF;
                } else {
                    // scl/applications/App/containers/Container ==> distantScl/scls/scl/applications/AppAnnc/containers
                    partialPath = Refs.CSES_REF+"/" + uri.split(Refs.CONTAINERS_REF)[0]+"Annc"+Refs.CONTAINERS_REF;
                }
            }
            // AccessRightAnnc
            if ("accessRights".equalsIgnoreCase(parentId)) {
                AccessRightAnnc accessRightAnnc = new AccessRightAnnc();
                accessRightAnnc.setLink(uri);
                accessRightAnnc.setId(resourceId + "Annc");
                accessRightAnnc.setSearchStrings(searchStrings);
                representation = XmlMapper.getInstance().objectToXml(accessRightAnnc);
                // Parent AccessRightAnnc partial path
                if((Constants.CSE_ID+Refs.ACCESSRIGHTS_REF).equalsIgnoreCase(parentUri)) {
                    // scl/accessRights/AccessRight ==> -distantScl/scls/scl/accessRights
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.ACCESSRIGHTS_REF;
                } else {
                    // scl/applications/App/accessRights/AccessRight ==> distantScl/scls/scl/applications/AppAnnc/accessRights
                    partialPath = Refs.CSES_REF+"/" + uri.split(Refs.ACCESSRIGHTS_REF)[0]+"Annc"+Refs.ACCESSRIGHTS_REF;
                }
            }
            // GroupAnnc
            if ("groups".equalsIgnoreCase(parentId)) {
                GroupAnnc groupAnnc = new GroupAnnc();
                groupAnnc.setLink(uri);
                groupAnnc.setId(resourceId + "Annc");
                groupAnnc.setSearchStrings(searchStrings);
                representation = XmlMapper.getInstance().objectToXml(groupAnnc);
                // Parent GroupAnnc partial path
                if((Constants.CSE_ID+Refs.GROUPS_REF).equalsIgnoreCase(parentUri)) {
                    // scl/groups/Group ==> distantScl/scls/scl/groups
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.GROUPS_REF;
                } else {
                    // scl/applications/App/groups/Group ==> distantScl/scls/scl/applications/AppAnnc/groups
                    partialPath = Refs.CSES_REF+"/" + uri.split(Refs.GROUPS_REF)[0]+"Annc"+Refs.GROUPS_REF;
                }
            }

            final String resourceAnncRepresentation = representation;
            // Send to the remote scls
            for (int i=uniqueReferencesList.size()-1; i>=0; i--) {
                final String hostingCse = uniqueReferencesList.get(i);
                final String hostingCseURI = Constants.CSE_ID+Refs.CSES_REF+"/"+hostingCse;
                EntityManager em = DBAccess.createEntityManager();
                em.getTransaction().begin();
                final Cse cse = DAOFactory.getCseDAO().find(hostingCseURI, em);
                em.close();

                if (cse != null) {
                    final String targetId = cse.getLink() + partialPath;
                    new Thread() {
                        public void run() {
                            // Set the request Base
                            String base = cse.getPocs().getReference().get(0)+ "/";
                            csenewList.getReference().add(hostingCse);
                            // Set the Request
                            RequestIndication requestIndication = new RequestIndication();
                            requestIndication.setMethod("CREATE");
                            requestIndication.setRequestingEntity(decodedRequestingEntity);
                            requestIndication.setTargetID(targetId);
                            requestIndication.setRepresentation(resourceAnncRepresentation);
                            requestIndication.setBase(base);

                            LOGGER.info("Annoncement Request\n:"+requestIndication);
                            // Send the request
                            ResponseConfirm response = new RestClient().sendRequest(requestIndication);
                            LOGGER.info("Annoncement Response:\n"+response.toString());
                        }
                    }.start();
                } else {
                    // Remove unregistered SCL from sclList
                    uniqueReferencesList.remove(uniqueReferencesList.indexOf(uniqueReferencesList.get(i)));
                }
            }
            announceTo.setCseList(csenewList);
            LOGGER.info("check3****************"+csenewList);
        }
        return announceTo;
    }


    /**
     * De-Announces the deleted resource.
     * @param announceTo - sclId target .
     * @param uri - resource uri.
     * @param requestingEntity - Requesting Entity
     */
    public void deAnnounce(AnnounceTo announceTo, String uri,String requestingEntity) {
        // De-Announcement to remote scls in sclList
        if (!announceTo.getCseList().getReference().isEmpty()) {

            String resourceId = uri.split("/")[uri.split("/").length - 1];
            String parent = uri.split("/" + resourceId)[0];
            String parentId = parent.split("/")[parent.split("/").length - 1];
            String partialPath = null;

            final String resourceAnncId = resourceId + "Annc";
            // ApplicationAnnc
            if ("applications".equalsIgnoreCase(parentId)) {
                // e.g. /scls/scl/applications/AppAnnc
                partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.APPLICATIONS_REF+"/"+resourceAnncId;
            }
            // ContainerAnnc
            if ("containers".equalsIgnoreCase(parentId)) {
                // AccessRightAnnc partial path
                if((Constants.CSE_ID+Refs.CONTAINERS_REF).equalsIgnoreCase(uri.split("/"+resourceId)[0])) {
                    // scl/containers/Container ==> -distantScl/scls/scl/containers/ContainerAnnc
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.CONTAINERS_REF+"/"+resourceAnncId;
                } else {
                    // scl/applications/App/containers/Container ==> distantScl/scls/scl/applications/AppAnnc/containers/ContainerAnnc
                    partialPath = Refs.CSES_REF+"/"+uri.split(Refs.CONTAINERS_REF)[0]+"Annc"+Refs.CONTAINERS_REF+"/"+resourceAnncId;
                }
            }
            // AccessRightAnnc
            if ("accessRights".equalsIgnoreCase(parentId)) {
                // AccessRightAnnc partial path
                if((Constants.CSE_ID+Refs.ACCESSRIGHTS_REF).equalsIgnoreCase(uri.split("/"+resourceId)[0])) {
                    // scl/accessRights/AccessRight ==> -distantScl/scls/scl/accessRights/AccessRightAnnc
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+Refs.ACCESSRIGHTS_REF+"/"+resourceAnncId;
                } else {
                    // scl/applications/App/accessRights/AccessRight ==> distantScl/scls/scl/applications/AppAnnc/accessRights/AccessRightAnnc
                    partialPath = Refs.CSES_REF+"/" + uri.split(Refs.ACCESSRIGHTS_REF)[0]+"Annc"+Refs.ACCESSRIGHTS_REF+"/"+resourceAnncId;
                }
            }
            // GroupAnnc
            if ("groups".equalsIgnoreCase(parentId)) {
                // GroupAnnc partial path
                if((Constants.CSE_ID+Refs.GROUPS_REF).equalsIgnoreCase(uri.split("/"+resourceId)[0])) {
                    // scl/groups/Group ==> distantScl/scls/scl/groups/GroupAnnc
                    partialPath = Refs.CSES_REF+"/"+Constants.CSE_ID+"/groups/"+resourceAnncId;
                } else {
                    // scl/applications/App/groups/Group ==> distantScl/scls/scl/applications/AppAnnc/groups/GroupAnnc
                    partialPath = Refs.CSES_REF+"/" + uri.split(Refs.GROUPS_REF)[0]+"Annc"+"/groups/"+resourceAnncId;
                }
            }
            // Retrieve the scls from the SclList without redundancies
            final ArrayList<String> uniqueReferencesList = new ArrayList<String>(new HashSet<String>(announceTo.getCseList().getReference()));
            final String decodedRequestingIndication = requestingEntity;

            for (int i = uniqueReferencesList.size() - 1; i >= 0; i--) {
                final String hostingCse = uniqueReferencesList.get(i);
                final String hostingCseURI = Constants.CSE_ID+Refs.CSES_REF+"/"+hostingCse;
                EntityManager em = DBAccess.createEntityManager();
                em.getTransaction().begin();
                final Cse cse = DAOFactory.getCseDAO().find(hostingCseURI, em);
                em.close();

                if (cse != null) {
                    final String targetId = cse.getLink()+partialPath;
                    new Thread() {
                        public void run() {
                            // Set the request Base
                            String base = cse.getPocs().getReference().get(0)+ "/";
                            // Set the Request
                            RequestIndication requestIndication = new RequestIndication();
                            requestIndication.setMethod("DELETE");
                            requestIndication.setRequestingEntity(decodedRequestingIndication);
                            requestIndication.setTargetID(targetId);
                            requestIndication.setBase(base);

                            LOGGER.info("Annoncement Request:\n"+ requestIndication);
                            // Send the Request
                            ResponseConfirm response = new RestClient().sendRequest( requestIndication);
                            LOGGER.info("Annoucement Response:\n"+ response);
                        }
                    }.start();
                }
            }
        }
    }
}
