// Vorgangsanlage issuetype 10301 Induktionsofen Verschleißfutter Neuzustellung

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade");  
def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"); 
def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);
def issueManager = ComponentAccessor.getIssueManager()

def createDateValue(attribute, date) {
	def dateValue = attribute.createObjectAttributeValueBean()
	dateValue.setDateValue(date)
	return dateValue
}

def createObjectValueReference(attribute, customFieldValues) {
	def objectReferenceValue = attribute.createObjectAttributeValueBean()
	objectReferenceValue.setReferencedObjectBeanId(customFieldValues.get(0).getId());
	return objectReferenceValue
}

def getAttributeByInsightObject(insightObject, attributName, objectTypeAttributeFacade, objectFacade) {
	def typeAttribute = objectTypeAttributeFacade.loadObjectTypeAttributeBean(insightObject.getObjectTypeId(), attributName)

	def attribute = null;
	try {
		attribute = objectFacade.loadObjectAttributeBean(insightObject.getId(), typeAttribute.getId())
		if (attribute == null) {
                        attribute = insightObject.createObjectAttributeBean(objectTypeAttributeFacade.loadObjectTypeAttributeBean(typeAttribute.getId()));
                }
	} catch (Exception ee) {
		attribute = insightObject.createObjectAttributeBean(objectTypeAttributeFacade.loadObjectTypeAttributeBean(typeAttribute.getId()));
	}
	return attribute
}

def getCustomFieldValue(customFieldId) {
	CustomField customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(customFieldId);
	return issue.getCustomFieldValue(customField)
}

def setAttributeValue(attribute, value, objectFacade) {
	def values = attribute.getObjectAttributeValueBeans();
	values.clear();
	values.add(value);
	attribute.setObjectAttributeValueBeans(values);
	try {
		objectFacade.storeObjectAttributeBean(attribute);
	} catch (Exception exception) {
		log.warn("Could not update object attribute due to validation exception:" + exception.getMessage());
	}
}

def getInboundReferencedObject(insightObject, referenceType, objectFacade) {
	def inboundReferencedObjects = objectFacade.findObjectInboundReferencedBeans(insightObject.getId(), 0, 9999, 0, true)
	for (def referencedObject : inboundReferencedObjects) {
		if (referencedObject.getObjectTypeAttributeBean().getName() == referenceType) {
			return referencedObject.getObjectBean()
		}
	}
	return null
}

def induktionsofenTiegeleinsatzInsightObject = getCustomFieldValue(10522).get(0)

//"Zustellung Verschleissfutter seit" von "Induktionsofen Tiegeleinsatz" auf den Wert aus dem Custom Field "Vorgangs-Zeitstempel" setzen
def zustellungVerschleissfutterSeitAttribut = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Zustellung Verschleissfutter seit", objectTypeAttributeFacade, objectFacade)
def vorgangsZeitstempelValue = createDateValue(zustellungVerschleissfutterSeitAttribut, getCustomFieldValue(10403))
setAttributeValue(zustellungVerschleissfutterSeitAttribut, vorgangsZeitstempelValue, objectFacade);

//"Verschleissfutter Material" von "Induktionsofen Tiegeleinsatz" auf den Wert aus dem Custom Field "Induktionsofen Verschleissfutter Material" setzen
def verschleissfutterMaterialAttribut = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Verschleissfutter Material", objectTypeAttributeFacade, objectFacade)
def verschleissfutterMaterialValue = createObjectValueReference(verschleissfutterMaterialAttribut, getCustomFieldValue(10524))
setAttributeValue(verschleissfutterMaterialAttribut, verschleissfutterMaterialValue, objectFacade);


//"Verschleissfutter Modell" von "Induktionsofen Tiegeleinsatz" auf den Wert aus dem Custom Field "Induktionsofen Modell Verschleissfutter" setzen
def verschleissfutterModellAttribut = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Verschleissfutter Modell", objectTypeAttributeFacade, objectFacade)
def verschleissfutterModellValue = createObjectValueReference(verschleissfutterModellAttribut, getCustomFieldValue(10523))
setAttributeValue(verschleissfutterModellAttribut, verschleissfutterModellValue, objectFacade);


//Summary setzen
issue.setSummary("Neuzustellung \"" + induktionsofenTiegeleinsatzInsightObject.getName() + "\" \"" + getCustomFieldValue(10523).get(0).getName() + "\" mit \"" + getCustomFieldValue(10524).get(0).getName() + "\"");


//Bauteil(e) ergÃ¤nzen um das Anlage-Objekt "Einbauposition (Ausbau)" und "Equipment zum Einbau" (Das Original-Object, nicht das Detail-Objekt!)
def bauteilList = [] as ArrayList

def beschreibtFromInduktionsofenTiegeleinsatzAttribute = objectFacade.loadObjectAttributeBean(induktionsofenTiegeleinsatzInsightObject.getId(), "Beschreibt")
if (beschreibtFromInduktionsofenTiegeleinsatzAttribute!= null && beschreibtFromInduktionsofenTiegeleinsatzAttribute.getObjectAttributeValueBeans().size() > 0) {
        def induktionsofenTiegeleinsatzInsightObjectAnlage = objectFacade.loadObjectBean(beschreibtFromInduktionsofenTiegeleinsatzAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
        if (induktionsofenTiegeleinsatzInsightObjectAnlage != null) {
                bauteilList.add(induktionsofenTiegeleinsatzInsightObjectAnlage)
        }
}

def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10402);
issue.setCustomFieldValue(bauteileCustomField, bauteilList)


//Issue speichern
issue.store();
def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)



return true;
