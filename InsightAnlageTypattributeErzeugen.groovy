import Constants;

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

// Anlage Untertypen abfragen

String QueryUntertypen = """
{ "page": 1, "asc": 1, "objectTypeId": 63, "filters": [ { "objectTypeAttributeId": 111, "selectedValues": [ "%" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""

def HttpURLConnection connRESTUntertypen = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connRESTUntertypen.setDoOutput(true)
connRESTUntertypen.setRequestProperty("Content-Type", "application/json")
connRESTUntertypen.setRequestProperty("Authorization", "Basic ${authString}")

// Teil 1: Map mit Untertypen erzeugen
def wrUntertypen = new DataOutputStream(connRESTUntertypen.getOutputStream())
wrUntertypen.writeBytes(QueryUntertypen)
wrUntertypen.flush()
wrUntertypen.close()

def RESTUntertypen = new groovy.json.JsonSlurper().parse(connRESTUntertypen.getInputStream())

def MapUntertypen = new HashMap()
def String NameUntertyp=""
def String KlasseDetails=""
def Integer IdUntertyp=0

RESTUntertypen.objectEntries.each { untertyp ->
    IdUntertyp = untertyp.id
    untertyp.attributes.each {attribut ->
      if (attribut.objectTypeAttributeId==111) {
          NameUntertyp = attribut.objectAttributeValues[0].value.toString()
      }
      if (attribut.objectTypeAttributeId==695) {
          KlasseDetails = attribut.objectAttributeValues[0].value.toString()
      }
    }
    MapUntertypen.put (IdUntertyp,[IdUntertyp:IdUntertyp, NameUntertyp:NameUntertyp, KlasseDetails:KlasseDetails, KlassenIdDetails:null, AttributIdName:null, AttributIdUntertyp:null, AttributIdBeschreibt:null])
}

//log.debug MapUntertypen.toString()

// Teil 2: Map mit Klassen für Detailinformationen erzeugen und IDs zu den Untertypen ablegen

def HttpURLConnection connRESTKlassen = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectschema/1/objecttypes/flat").openConnection()
connRESTKlassen.setDoOutput(false)
connRESTKlassen.setRequestProperty("Content-Type", "application/json")
connRESTKlassen.setRequestProperty("Authorization", "Basic ${authString}")

def RESTKlassen = new groovy.json.JsonSlurper().parse(connRESTKlassen.getInputStream())

def MapKlassen = new HashMap()

RESTKlassen.each {klasse ->
  MapKlassen.put (klasse.name, klasse.id)
}

MapUntertypen.each {untertyp ->
    schluessel=untertyp.key
    wert = untertyp.value
    wert.KlassenIdDetails = MapKlassen.get(wert.KlasseDetails)
    MapUntertypen.put (schluessel, wert)
}

// Attribut-ID für das Namensattribut holen

def HttpURLConnection connRESTAttribute

MapUntertypen.each {untertyp ->
    schluessel = untertyp.key
    wert = untertyp.value
    connRESTAttribute = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objecttype/" + untertyp.value.KlassenIdDetails + "/attributes").openConnection()
    connRESTAttribute.setDoOutput(false)
    connRESTAttribute.setRequestProperty("Content-Type", "application/json")
    connRESTAttribute.setRequestProperty("Authorization", "Basic ${authString}")

    RESTAttribute = new groovy.json.JsonSlurper().parse(connRESTAttribute.getInputStream())

    RESTAttribute.each {attribut ->
        if (attribut.name=="Name") {
            wert.AttributIdName = attribut.id
        }
        if (attribut.name=="Anlage - Untertyp") {
            wert.AttributIdUntertyp = attribut.id
        }
        if (attribut.name=="Beschreibt") {
            wert.AttributIdBeschreibt = attribut.id
        }

    }
    MapUntertypen.put(schluessel, wert)
}

//log.debug MapUntertypen

// Teil 3: Hole Objekte vom Typ "Anlage"

String QueryAnlage = """
  { "page": 1, "asc": 1, "objectTypeId": 41, "filters": [], "resultsPerPage": 9999, "includeAttributes": true }
"""

def HttpURLConnection connRESTAnlage = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connRESTAnlage.setDoOutput(true)
connRESTAnlage.setRequestProperty("Content-Type", "application/json")
connRESTAnlage.setRequestProperty("Authorization", "Basic ${authString}")

def wrAnlage = new DataOutputStream(connRESTAnlage.getOutputStream())
wrAnlage.writeBytes(QueryAnlage)
wrAnlage.flush()
wrAnlage.close()

def RESTAnlage = new groovy.json.JsonSlurper().parse(connRESTAnlage.getInputStream())

String QueryDetailobjektVorlage = """
  { "page": 1, "asc": 1, "objectTypeId": @@Objekttyp@@, "filters": [{"objectTypeAttributeId":@@IdAttributBeschreibt@@, "selectedValues": ["@@Filterwert@@"]}], "resultsPerPage": 9999, "includeAttributes": true }
"""
//  { "page": 1, "asc": 1, "objectTypeId": @@Objekttyp@@, "filters": [{"objectTypeAttributeId":@@IdAttributBeschreibt@@, "selectedValues": ["@@Filterwert@@]}], "resultsPerPage": 9999, "includeAttributes": true }

String QueryDetailobjekt = ""


def Integer IdAnlage=-1
def String NameAnlage=""
def Integer IdDetailobjekt=-1

RESTAnlage.objectEntries.each { anlage ->
    IdAnlage = anlage.id
    NameAnlage=anlage.name
    IdUntertyp=-1
    IdDetailobjekt=-1
    anlage.attributes.each {attribut ->
      if (attribut.objectTypeAttributeId==118) {
          if (attribut.objectAttributeValues[0]!=null) {
            IdUntertyp = attribut.objectAttributeValues[0].referencedObject.id
          }
      }
    }
    if (IdUntertyp!=-1) {
        // Wir suchen nach einem passenden Detailobjekt, das in der richtigen Klasse ist und unser Objekt "Anlage" schon beschreibt
        QueryDetailobjekt = QueryDetailobjektVorlage.replace("@@Objekttyp@@",MapUntertypen.get(IdUntertyp).KlassenIdDetails.toString())
        QueryDetailobjekt = QueryDetailobjekt.replace("@@Filterwert@@",anlage.id.toString())
        QueryDetailobjekt = QueryDetailobjekt.replace("@@IdAttributBeschreibt@@",MapUntertypen.get(IdUntertyp).AttributIdBeschreibt.toString())
	//log.debug QueryDetailobjekt
        HttpURLConnection connRESTDetailobjekt = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
        connRESTDetailobjekt.setDoOutput(true)
        connRESTDetailobjekt.setRequestProperty("Content-Type", "application/json")
        connRESTDetailobjekt.setRequestProperty("Authorization", "Basic ${authString}")
	wrDetailobjekt = new DataOutputStream(connRESTDetailobjekt.getOutputStream())
	wrDetailobjekt.writeBytes(QueryDetailobjekt)
	RESTDetailobjekt = new groovy.json.JsonSlurper().parse(connRESTDetailobjekt.getInputStream())
	
	
	// Kein Detailobjekt gefunden: wir wollen ein neues Objekt anlegen
        if (RESTDetailobjekt.totalFilterCount==0) {
            log.debug anlage.name
            // Wir haben keine Detailobjekt gefunden, darum wollen wir jetzt eins anlegen
            AttributeRoh = []
            MapObjekt = new HashMap()
            MapAttribut = new HashMap()
            MapWert = new HashMap()
            AttributeRoh.add([id:MapUntertypen.get(IdUntertyp).AttributIdName , ListEinzelwerte: [anlage.name]])
            AttributeRoh.add([id:MapUntertypen.get(IdUntertyp).AttributIdUntertyp, ListEinzelwerte: [IdUntertyp]])
            AttributeRoh.add([id:MapUntertypen.get(IdUntertyp).AttributIdBeschreibt, ListEinzelwerte: [IdAnlage]])

            MapObjekt = new HashMap()
            MapAttribut = new HashMap()
            MapWert = new HashMap()
            ListAttribute = []

            MapObjekt.put("objectTypeId", MapUntertypen.get(IdUntertyp).KlassenIdDetails)
            AttributeRoh.each { attribut ->
                MapAttribut = ["objectTypeAttributeId": attribut.id]
                ListWerte = []
                attribut.ListEinzelwerte.each { einzelwert ->
                    MapWert = ["value": einzelwert]
                    ListWerte.add(MapWert)
                }
                MapAttribut.put("objectAttributeValues", ListWerte)
                ListAttribute.add(MapAttribut)
            }
            MapObjekt.put("attributes", ListAttribute)

            builderObjektanlage = new groovy.json.JsonBuilder(MapObjekt)
            connRESTObjektanlage = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
            connRESTObjektanlage.setDoOutput(true)
            connRESTObjektanlage.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connRESTObjektanlage.setRequestProperty("Authorization", "Basic ${authString}")

            wrObjektanlage = new DataOutputStream(connRESTObjektanlage.getOutputStream())
            wrObjektanlage.writeBytes(builderObjektanlage.toPrettyString())
            log.debug builderObjektanlage.toPrettyString()
            wrObjektanlage.flush()
            wrObjektanlage.close()

            ObjektDetails = new groovy.json.JsonSlurper().parse(connRESTObjektanlage.getInputStream())
            log.debug ObjektDetails.toString() 

        } 
    }
}

return true
