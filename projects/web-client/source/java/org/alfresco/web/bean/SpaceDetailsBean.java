/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.bean;

import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.web.bean.repository.Node;
import org.alfresco.web.ui.common.component.UIActionLink;

/**
 * Back bean provided access to the details of a Space
 * 
 * @author Kevin Roast
 */
public class SpaceDetailsBean
{
   /** BrowseBean instance */
   private BrowseBean browseBean;
   
   /** The NavigationBean bean reference */
   private NavigationBean navigator;
   
   /** PermissionService bean reference */
   private PermissionService permissionService;
   
   
   // ------------------------------------------------------------------------------
   // Bean property getters and setters 
   
   /**
    * Sets the BrowseBean instance to use to retrieve the current Space
    * 
    * @param browseBean BrowseBean instance
    */
   public void setBrowseBean(BrowseBean browseBean)
   {
      this.browseBean = browseBean;
   }
   
   /**
    * @param navigator The NavigationBean to set.
    */
   public void setNavigator(NavigationBean navigator)
   {
      this.navigator = navigator;
   }
   
   /**
    * @param permissionService      The PermissionService to set.
    */
   public void setPermissionService(PermissionService permissionService)
   {
      this.permissionService = permissionService;
   }
   
   /**
    * Returns the Space this bean is currently representing
    * 
    * @return The Space Node
    */
   public Node getSpace()
   {
      return this.browseBean.getActionSpace();
   }
   
   /**
    * Returns the id of the current document
    * 
    * @return The id
    */
   public String getId()
   {
      return getSpace().getId();
   }
   
   /**
    * Returns the name of the current document
    * 
    * @return Name of the current document
    */
   public String getName()
   {
      return getSpace().getName();
   }
   
   
   // ------------------------------------------------------------------------------
   // Action event handlers
   
   /**
    * Navigates to next item in the list of Spaces
    */
   public void nextItem(ActionEvent event)
   {
      UIActionLink link = (UIActionLink)event.getComponent();
      Map<String, String> params = link.getParameterMap();
      String id = params.get("id");
      if (id != null && id.length() != 0)
      {
         List<Node> nodes = this.browseBean.getNodes();
         if (nodes.size() > 1)
         {
            // perform a linear search - this is slow but stateless
            // otherwise we would have to manage state of last selected node
            // this gets very tricky as this bean is instantiated once and never
            // reset - it does not know when the document has changed etc.
            for (int i=0; i<nodes.size(); i++)
            {
               if (id.equals(nodes.get(i).getId()) == true)
               {
                  Node next;
                  // found our item - navigate to next
                  if (i != nodes.size() - 1)
                  {
                     next = nodes.get(i + 1);
                  }
                  else
                  {
                     // handle wrapping case
                     next = nodes.get(0);
                  }
                  
                  // prepare for showing details for this node
                  this.browseBean.setupSpaceAction(next.getId(), false);
               }
            }
         }
      }
   }
   
   /**
    * Navigates to the previous item in the list Spaces
    */
   public void previousItem(ActionEvent event)
   {
      UIActionLink link = (UIActionLink)event.getComponent();
      Map<String, String> params = link.getParameterMap();
      String id = params.get("id");
      if (id != null && id.length() != 0)
      {
         List<Node> nodes = this.browseBean.getNodes();
         if (nodes.size() > 1)
         {
            // see above
            for (int i=0; i<nodes.size(); i++)
            {
               if (id.equals(nodes.get(i).getId()) == true)
               {
                  Node previous;
                  // found our item - navigate to previous
                  if (i != 0)
                  {
                     previous = nodes.get(i - 1);
                  }
                  else
                  {
                     // handle wrapping case
                     previous = nodes.get(nodes.size() - 1);
                  }
                  
                  // show details for this node
                  this.browseBean.setupSpaceAction(previous.getId(), false);
               }
            }
         }
      }
   }
   
   /**
    * Action handler to clear the current Space properties before returning to the browse screen,
    * as the user may have modified the properties! 
    */
   public String closeDialog()
   {
      this.navigator.resetCurrentNodeProperties();
      return "browse";
   }
}
