import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
//Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.web.api.facade.ObjectFacade");
//def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
//Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.web.api.facade.ObjectTypeAttributeFacade");
//def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);
//def issueManager = ComponentAccessor.getIssueManager()

/*
def createBooleanValue(attribute, trueOrFalse) {
    def boolValue = attribute.createObjectAttributeValueBean()
    boolValue.setBooleanValue(trueOrFalse)
    return boolValue
}

def createDateValue(attribute, date) {
    def dateValue = attribute.createObjectAttributeValueBean()
    dateValue.setDateValue(date)
    return dateValue
}

def createObjectValueReference(attribute, customFieldValues) {
    def objectReferenceValue = attribute.createObjectAttributeValueBean()
    objectReferenceValue.setReferencedObjectBean(customFieldValues.get(0).getObjectSmallBean());
    return objectReferenceValue
}

def getAttributeByCustomFieldObject(customFieldId, attributName, objectTypeAttributeFacade, objectFacade) {
    CustomField customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(customFieldId);
    def insightObject = issue.getCustomFieldValue(customField).get(0);
    def typeAttribute = objectTypeAttributeFacade.loadObjectTypeAttributeBean(insightObject.getObjectTypeId(), attributName)

    def attribute = null;
    try {
        attribute = objectFacade.loadObjectAttributeBean(insightObject.getId(), typeAttribute.getId())
    } catch (Exception ee) {
        attribute = insightObject.createObjectAttributeBean(objectTypeAttributeFacade.loadObjectTypeAttributeBean(typeAttribute.getId()));
    }
    return attribute
} */

def getCustomFieldValue(customFieldId) {
    CustomField customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(customFieldId);
    return issue.getCustomFieldValue(customField)
}

/*

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
} */

/*
//"eingebaut in" am Attribut von "Equipment zum Einbau" auf "true" setzen
def istEingebautAttribute = getAttributeByCustomFieldObject(10102, "ist eingebaut", objectTypeAttributeFacade, objectFacade)
def istEingebautTrueValue = createBooleanValue(istEingebautAttribute, true)
setAttributeValue(istEingebautAttribute, istEingebautTrueValue, objectFacade);


//"Einbaustatus seit" am Attribut von "Equipment zum Einbau" auf den Wert aus dem Custom Field "Vorgangs-Zeitstempel" setzen
def einbaustatusSeitAttribut = getAttributeByCustomFieldObject(10102, "Einbaustatus seit", objectTypeAttributeFacade, objectFacade)
def vorgangsZeitstempelValue = createDateValue(einbaustatusSeitAttribut, getCustomFieldValue(10403))
setAttributeValue(einbaustatusSeitAttribut, vorgangsZeitstempelValue, objectFacade);


//"ist eingebaut in" am Attribut von "Equipment zum Einbau" auf den Wert aus dem Custom Field "Einbauposition" setzen
def istEingebautInAttribut = getAttributeByCustomFieldObject(10102, "ist eingebaut in", objectTypeAttributeFacade, objectFacade)
def einbaupositionValue = createObjectValueReference(istEingebautInAttribut, getCustomFieldValue(10106))
setAttributeValue(istEingebautInAttribut, einbaupositionValue, objectFacade);


//"ist belegt" am Attribut von "Einbauposition" auf "true" setzen
def istBelegtAttribute = getAttributeByCustomFieldObject(10106, "ist belegt", objectTypeAttributeFacade, objectFacade)
def isBelegtTrueValue = createBooleanValue(istEingebautAttribute, true)
setAttributeValue(istBelegtAttribute, isBelegtTrueValue, objectFacade);


//Bauteil(e) ergänzen um das Anlage-Objekt "Einbauposition" und "Equipment zum Einbau" (Das Original-Object, nicht das Detail-Objekt!)
def bauteilList = [] as ArrayList
def equipmentZumEinbauInsightObject = getInboundReferencedObject(getCustomFieldValue(10102).get(0), "wird beschrieben in", objectFacade)
if (equipmentZumEinbauInsightObject != null) {
    bauteilList.add(equipmentZumEinbauInsightObject)
}

def einbaupositionInsightObject = getInboundReferencedObject(getCustomFieldValue(10106).get(0), "wird beschrieben in", objectFacade)
if (einbaupositionInsightObject != null) {
    bauteilList.add(einbaupositionInsightObject)
}

def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10402);
issue.setCustomFieldValue(bauteileCustomField, bauteilList)
*/

dokumentart = getCustomFieldValue(11000)[0].getName()
bauteilname = getCustomFieldValue(10402)[0].getName()

issue.setSummary(dokumentart + " - " + bauteilname)

//Issue speichern
issue.store();

//def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
//issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)



return true;
