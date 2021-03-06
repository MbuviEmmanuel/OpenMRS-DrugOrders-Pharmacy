<%
    ui.decorateWith("appui", "standardEmrPage");
    ui.includeCss("drugorders", "pharmacy.css")
    ui.includeJavascript("drugorders", "pharmacy.js")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("Pharmacy") }" , link: '${ui.pageLink("drugorders", "pharmacyHome")}'},
        { label: "${ ui.format(patient.familyName) }, ${ ui.format(patient.givenName) }"}
    ];
     
    var patient = { id: ${ patient.id } };

</script>
 
${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }

<div class="info-body">
    
    <!--
        Display the list of drugs the Patient is allergic to.
    -->

    <div id="allergyList">
        <strong>Drug Allergies:</strong>
        <% if (allergicDrugs == "null") { %>
            ${ ui.message("drugorders.allergies") }
        <% } else { %>
            <% allergicDrugs.each { allergy -> %>
                ${ allergy } 
            <% } %>
        <% } %>
    </div> <br/><br/>
    
    <div>
        <div id="line-break"></div>
        <h3>
            <i class="icon-medicine"></i>
            <strong>${ ui.message("ACTIVE DRUG ORDERS") }</strong>
        </h3>
        <div id="line-break"></div>
    </div> <br/>

    <div id="currentDrugOrdersWindow">
        ${ ui.includeFragment("drugorders", "ordererContact") }
        
        ${ ui.includeFragment("drugorders", "currentDrugOrders") } <br/>
    </div>
    
    <div id="pharmaGroupViewWindow">        
        ${ ui.includeFragment("drugorders", "selectedOrdersView") }
    </div>

</div>