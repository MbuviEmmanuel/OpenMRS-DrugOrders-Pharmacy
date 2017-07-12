/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openmrs.module.drugorders.page.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.openmrs.DrugOrder;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.module.allergyapi.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.allergyapi.Allergies;
import org.openmrs.module.allergyapi.Allergy;
import org.openmrs.module.drugorders.api.drugordersService;
import org.openmrs.module.drugorders.drugorders;
import org.openmrs.module.uicommons.util.InfoErrorMessageUtil;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author harini-parthasarathy
 */
public class PharmacyPatientPageController {

    public void controller(PageModel model, HttpSession session,
            @RequestParam("patientId") Patient patient, @SpringBean("allergyService") PatientService patientService,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "groupCheckBox", required=false) long[] groupCheckBox,
            @RequestParam(value = "pharmaGroupAction", required = false) String groupAction,
            @RequestParam(value = "groupComments", required = false) String groupComments,
            @RequestParam(value = "drugExpiryDate", required = false) Date[] drugExpiryDate,
            @RequestParam(value = "commentForPatient", required = false) String[] commentForPatient) {

        /*
          Get the list of drugs the Patient is allergic to
        */
        Allergies allergies = patientService.getAllergies(patient);
        ArrayList<String> allergicDrugList = new ArrayList<>();
        
        if(allergies.size() > 0){
            for(Allergy allergy : allergies){
                allergicDrugList.add(allergy.getAllergen().toString().toUpperCase());
                model.addAttribute("allergicDrugs", allergicDrugList);
            }
        } else {
            model.addAttribute("allergicDrugs", "null");
        }
        
        if (StringUtils.isNotBlank(action)) {
            try {
                /*
                  If an action (Dispatch, On-Hold, Discard is selected and confirmed, take actions.
                */
                if ("Confirm".equals(action)) {
                    /*
                      If one or more check-boxes corresponding to a drug or a drug order is checked, retrieve the order ID.
                    */
                    if(groupCheckBox.length > 0){
                        for(int i=0;i<groupCheckBox.length;i++){
                            
                            int orderID = Integer.parseInt(Long.toString(groupCheckBox[i]));
                            drugorders drugorder = Context.getService(drugordersService.class).getDrugOrderByOrderID(orderID);
                            
                            /*
                              If the order is selected to be put on hold or requested to be discarded,
                               apply the appropriate status on the order and save the comments.
                            */
                            switch (groupAction) {
                                case "Discard":
                                    drugorder.setForDiscard(1);
                                    // If order was previously set on hold, remove the on-hold flag.
                                    if(drugorder.getOnHold() == 1)
                                        drugorder.setOnHold(0);
                                    // If comments to discard the order are provided, save the comments.
                                    if(groupComments != null)
                                        drugorder.setCommentForOrderer(groupComments);
                                    break;
                                case "On Hold":
                                    drugorder.setOnHold(1);
                                    // If the order was previously set to be discarded, remove the to-discard flag.
                                    if(drugorder.getForDiscard()== 1)
                                        drugorder.setForDiscard(0);
                                    // If comments to put the order on hold are provided, save the comments.
                                    if(groupComments != null)
                                        drugorder.setCommentForOrderer(groupComments);
                                    break;
                                case "Dispatch":
                                    // If the order was previously set to be discarded, remove the to-discard flag.
                                    if(drugorder.getForDiscard() == 1)
                                        drugorder.setForDiscard(0);
                                    // If order was previously set on hold, remove the on-hold flag.
                                    else if(drugorder.getOnHold() == 1)
                                        drugorder.setOnHold(0);
                                    /*
                                      If the order is selected to be dispatched, set the last dispatch date and decrement the allowed number of refills.
                                      Update the order status.
                                    */
                                    if (drugorder.getRefill() > 0) {
                                        drugorder.setLastDispatchDate(Calendar.getInstance().getTime());
                                        drugorder.setRefill(drugorder.getRefill() - 1);
                                    }
                                    else {
                                        switch (drugorder.getOrderStatus()) {
                                            case "Active":
                                                drugorder.setOrderStatus("Non-Active");
                                                break;
                                            case "Active-Group":
                                                drugorder.setOrderStatus("Non-Active-Group");
                                                break;
                                            case "Active-Plan":
                                                drugorder.setOrderStatus("Non-Active-Plan");
                                                break;
                                        }
                                        Context.getOrderService().voidOrder(Context.getOrderService().getOrder(drugorder.getOrderId()), "No Longer Active");
                                    }   
                                    // Save the drug expiry date and comments entered for the Patient.
                                    drugorder.setDrugExpiryDate(drugExpiryDate[i]);
                                    drugorder.setCommentForPatient(commentForPatient[i]);
                                    // Print the order details prescription.
                                    printOrder(drugorder.getOrderId());
                                    break;
                            }
                            Context.getService(drugordersService.class).saveDrugOrder(drugorder);
                        }
                    }
                    InfoErrorMessageUtil.flashInfoMessage(session, "Order Status - " + groupAction);
                }
            } 
            catch (NumberFormatException | APIException ex) {
                Logger.getLogger(PharmacyPatientPageController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        model.addAttribute("group_order_status", groupAction);
    }
    
    /*
      This function will send a command to the printer to print out the details of the drug order that is relavant to the Patient.
    */
    void printOrder(int orderID){
        
        // Fetch the details to be printed on the prescription.
        DrugOrder order = (DrugOrder) Context.getOrderService().getOrder(orderID);
        drugorders drugorder = Context.getService(drugordersService.class).getDrugOrderByOrderID(orderID);

        String OrderDetails = drugorder.getDrugName().getDisplayString() + " " + order.getDose() + " " + order.getDoseUnits().getDisplayString() + " " +
                order.getDuration() + " " + order.getDurationUnits().getDisplayString() + " " + order.getQuantity() + " " + order.getQuantityUnits() + "\n" +
                "Route: " + order.getRoute().getDisplayString() + " " + "Frequency: " + order.getFrequency().getName() + "\n" +
                "Start Date: " + drugorder.getStartDate().toString() + "\n" +
                "Patient Instructions: " + drugorder.getPatientInstructions();
            
        try {
            // Fetch the default print service.
            PrintService service = PrintServiceLookup.lookupDefaultPrintService();
            
            try {
                PrintRequestAttributeSet  pras = new HashPrintRequestAttributeSet();
                pras.add(new Copies(1));
                
                if(service != null){
                    byte[] is = OrderDetails.getBytes();
                    DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
                    Doc doc = new SimpleDoc(is, flavor, null);
                    DocPrintJob job = service.createPrintJob();
                    
                    job.print(doc, pras);
                }
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(PharmacyPatientPageController.class.getName()).log(Level.SEVERE, null, ex);
            } 
            
        } catch (PrintException ex) {
            Logger.getLogger(PharmacyPatientPageController.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.out.println(OrderDetails);
    }
}