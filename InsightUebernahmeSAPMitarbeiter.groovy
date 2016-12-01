import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.net.URL
import groovy.sql.Sql
import groovy.transform.ToString
import java.sql.Driver
import Constants

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

@ToString
class Mitarbeiter {
  Boolean Aktiv
  String AzPlan
  String Kostenstelle
  String Nachname
  String Organisation
  String PersNr
  String StatusMitarbeiter
  String Stelle
  String Vorname
  String SmartId
}

// Liste Mitarbeiter aus REST-Service holen

def HttpURLConnection connMitarbeiterListe = new URL("http://automat01/SchichtberichteService/MitarbeiterListe").openConnection()
connMitarbeiterListe.setDoOutput(false)
connMitarbeiterListe.setRequestProperty("Content-Type", "application/json")
def MitarbeiterListe = new groovy.json.JsonSlurper().parse(connMitarbeiterListe.getInputStream())
def ListMitarbeiter = []
if (MitarbeiterListe.isEmpty()==false) {
  MitarbeiterListe.each {maREST ->
    Mitarbeiter mitarbeiter = new Mitarbeiter()
    mitarbeiter.Aktiv = maREST.Aktiv
    mitarbeiter.AzPlan = maREST.AzPlan
    mitarbeiter.Kostenstelle = maREST.Kostenstelle
    mitarbeiter.Nachname = maREST.Nachname
    mitarbeiter.Organisation = maREST.Organisation
    mitarbeiter.PersNr = maREST.PersNr
    mitarbeiter.StatusMitarbeiter = maREST.StatusMitarbeiter
    mitarbeiter.Stelle = maREST.Stelle
    mitarbeiter.Vorname = maREST.Vorname
    ListMitarbeiter.add(mitarbeiter)
  }
}

String VorlageQueryMitarbeiter= """
{ "page": 1, "asc": 1, "objectTypeId": 102, "filters": [ { "objectTypeAttributeId": 210, "selectedValues": [ "@@Personalnummer@@" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""

// Abfrage existierende Mitarbeiter in Insight
ListMitarbeiter.each {mitarbeiter ->
  String QueryMitarbeiter = VorlageQueryMitarbeiter.replace("@@Personalnummer@@",mitarbeiter.PersNr.toString())
  HttpURLConnection connMitarbeiter= new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
  connMitarbeiter.setDoOutput(true)
  connMitarbeiter.setRequestProperty("Content-Type", "application/json");
  connMitarbeiter.setRequestProperty("Authorization", "Basic ${authString}")
  DataOutputStream wrMitarbeiter = new DataOutputStream(connMitarbeiter.getOutputStream())
  wrMitarbeiter.writeBytes(QueryMitarbeiter)
  wrMitarbeiter.flush()
  wrMitarbeiter.close()
  InsightMitarbeiter = new groovy.json.JsonSlurper().parse(connMitarbeiter.getInputStream())
  if (InsightMitarbeiter.totalFilterCount==1) {
    mitarbeiter.SmartId = InsightMitarbeiter.objectEntries[0].id
  }  
}

// Nicht relevante Einträge entfernen
ListLoeschkandidaten=[]
ListMitarbeiter.each {mitarbeiter ->
  //Wenn Mitarbeiter nicht aktiv und nicht in Smart angelegt: rauswerfen
  if ((mitarbeiter.Aktiv==false) && (mitarbeiter.SmartId==null)) {
    ListLoeschkandidaten.add(mitarbeiter)
  }
  // AZPlan "Frei" und nicht in Smart --> Fremdfirma --> raus
  if ((mitarbeiter.AzPlan=="Frei") && (mitarbeiter.SmartId==null)){
    ListLoeschkandidaten.add(mitarbeiter)
  }
}

ListLoeschkandidaten.each {mitarbeiter ->
  ListMitarbeiter.remove(mitarbeiter)
}

ListMitarbeiter.each {mitarbeiter ->
  if ((mitarbeiter.AzPlan=="Frei")){
    log.debug mitarbeiter
  }  
}

/*

if (MitarbeiterListe.isEmpty()==false) {
    MitarbeiterListe.each {mitarbeiter ->

      if ((mitarbeiter.Vorname!="unbekannt") && (mitarbeiter.Nachname!="unbekannt") && (mitarbeiter.Organisation!=null)) {
	
	  //Eigenschaften Mitarbeiter vorbesetzen
	
	  ArrayList attribute = []
	  attribute.add ([name:"Name", typ:"String", objectTypeAttributeId:205, attributIdInsight:null, inhaltDB:mitarbeiter.PersNr.toString() + " | " + mitarbeiter.Nachname + ", " + mitarbeiter.Vorname, inhaltInsight:"", flagAktualisiert:0])
	  attribute.add ([name:"Vorname", typ:"String", objectTypeAttributeId:209, attributIdInsight:null, inhaltDB:mitarbeiter.Vorname, inhaltInsight:"", flagAktualisiert:0])
	  attribute.add ([name:"PersNr", typ:"String", objectTypeAttributeId:210, attributIdInsight:null, inhaltDB:mitarbeiter.PersNr.toString(), inhaltInsight:"", flagAktualisiert:0])
	  attribute.add ([name:"Nachname", typ:"String", objectTypeAttributeId:211, attributIdInsight:null, inhaltDB:mitarbeiter.Nachname, inhaltInsight:"", flagAktualisiert:0])
	  attribute.add ([name:"aktiv", typ:"String", objectTypeAttributeId:482, attributIdInsight:null, inhaltDB:mitarbeiter.Aktiv.toString(), inhaltInsight:"", flagAktualisiert:0])
	
	  //log.debug attribute
	
	  // Abfrage existierende Mitarbeiter in Insight
	  String QueryMitarbeiter = VorlageQueryMitarbeiter.replace("@@Personalnummer@@",mitarbeiter.PersNr.toString())
          log.debug QueryMitarbeiter
	  HttpURLConnection connMitarbeiter= new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
	  connMitarbeiter.setDoOutput(true)
	  connMitarbeiter.setRequestProperty("Content-Type", "application/json");
	  connMitarbeiter.setRequestProperty("Authorization", "Basic ${authString}")
	  DataOutputStream wrMitarbeiter = new DataOutputStream(connMitarbeiter.getOutputStream())
	  wrMitarbeiter.writeBytes(QueryMitarbeiter)
	  wrMitarbeiter.flush()
	  wrMitarbeiter.close()
	  InsightMitarbeiter = new groovy.json.JsonSlurper().parse(connMitarbeiter.getInputStream())
	  log.debug InsightMitarbeiter
	
	  // Mitarbeiter in Insight vorhanden
	  if (InsightMitarbeiter.totalFilterCount==1) {
	    // Vorhandener Mitarbeiter wird aktualisiert
	      log.debug "Aktualisieren: Mitarbeiter " + mitarbeiter.Nachname + "," + mitarbeiter.Vorname
	      //Wir besorgen uns die aktuell im Insight vorhandenen Daten
	      InsightSAPWgrp.objectEntries[0].attributes.each {attributInsight ->
	        attribute.each {attribut ->
	          if (attributInsight.objectTypeAttributeId==attribut.objectTypeAttributeId) {
		    attribut.attributIdInsight=attributInsight.id
		    if (attribut.typ=="String") {
  		      attribut.inhaltInsight=attributInsight.objectAttributeValues[0]?.value
		    }
		    if (attribut.typ=="Referenz") {
  		      attribut.inhaltInsight=attributInsight.objectAttributeValues[0].referencedObject?.id.toString()
		    }
		    if ((attribut.inhaltDB!=attribut.inhaltInsight)) { // && (attribut.attributIdInsight!=null)
		      attribut.flagAktualisiert=1
		    }
		  }
	        }
	      }
	      attribute.each {attribut ->
	        if (attribut.flagAktualisiert==1) {
	          if (attribut.attributIdInsight!=null) {
		    //log.debug attribut
	            MapAttribut=new HashMap()
		    MapAttribut.put ("objectAttributeValues",[["value":attribut.inhaltDB]])
                    builderAttribut = new groovy.json.JsonBuilder (MapAttribut)
                    connAttribut = new URL("http://smart.smtdom.schwermetall.de/rest/insight/1.0/objectattribute/"+attribut.attributIdInsight.toString()).openConnection()
                    connAttribut.setDoOutput(true)
                    connAttribut.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connAttribut.setRequestProperty("Authorization", "Basic ${authString}")
                    connAttribut.setRequestMethod("PUT")
                    wrAttribut = new DataOutputStream(connAttribut.getOutputStream())
                    //log.debug builderAttribut.toPrettyString()
                    wrAttribut.writeBytes(builderAttribut.toPrettyString())
                    wrAttribut.flush()
                    wrAttribut.close()
                    AntwortAttribut = new groovy.json.JsonSlurper().parse(connAttribut.getInputStream())
	            //log.debug AntwortAttribut
                    log.debug  "Attribut " + attribut.attributIdInsight.toString() + " aktualisiert"
		  }
                  else {
	            MapAttribut=new HashMap()
		    MapAttribut.put ("objectId",InsightSAPWgrp.objectEntries[0].id)
		    MapAttribut.put ("objectTypeAttributeId",attribut.objectTypeAttributeId)
		    MapAttribut.put ("objectAttributeValues",[["value":attribut.inhaltDB]])
                    builderAttribut = new groovy.json.JsonBuilder (MapAttribut)
		    log.debug builderAttribut.toPrettyString()
                    connAttribut = new URL("http://smart.smtdom.schwermetall.de/rest/insight/1.0/objectattribute/create").openConnection()
                    connAttribut.setDoOutput(true)
                    connAttribut.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connAttribut.setRequestProperty("Authorization", "Basic ${authString}")
                    connAttribut.setRequestMethod("POST")
                    wrAttribut = new DataOutputStream(connAttribut.getOutputStream())
                    wrAttribut.writeBytes(builderAttribut.toPrettyString())
                    wrAttribut.flush()
                    wrAttribut.close()
                    AntwortAttribut = new groovy.json.JsonSlurper().parse(connAttribut.getInputStream())
	            //log.debug AntwortAttribut
                    log.debug  "Attribut angelegt"
                  }		
	       }
	     }
	     
	   }  
	  // Mitarbeiter in Insight nicht vorhanden
	  else {
	    log.debug "Mitarbeiter Neuanlage " + mitarbeiter
	    AttributeRoh = []
	    attribute.each {attribut ->
	      AttributeRoh.add([id:attribut.objectTypeAttributeId, value:attribut.inhaltDB])
	    }
            MapObjekt = new HashMap()
            MapAttribut = new HashMap()
            MapWert = new HashMap()

            MapObjekt.put ("objectTypeId",3)
            def ListAttribute=[]
            AttributeRoh.each {attribut ->
              MapAttribut = ["objectTypeAttributeId": attribut.id]
              MapWert = ["value": attribut.value]
              MapAttribut.put ("objectAttributeValues",[MapWert])
              ListAttribute.add(MapAttribut)
            }
            MapObjekt.put ("attributes",ListAttribute)

            builderAnlage = new groovy.json.JsonBuilder (MapObjekt)
	    //log.debug builderAnlage.toPrettyString()
	
            connAnlage = new URL("http://smart.smtdom.schwermetall.de/rest/insight/1.0/object/create").openConnection()
            connAnlage.setDoOutput(true)
            connAnlage.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connAnlage.setRequestProperty("Authorization", "Basic ${authString}")
	    wrAnlage = new DataOutputStream(connAnlage.getOutputStream())
	    wrAnlage.writeBytes(builderAnlage.toPrettyString())
	    wrAnlage.flush()
	    wrAnlage.close()
	    WarengruppeInsight = new groovy.json.JsonSlurper().parse(connAnlage.getInputStream())
	  }
	}  
    }
}
else {
    log.debug "Mitarbeiterliste ist leer!!!"
}

*/