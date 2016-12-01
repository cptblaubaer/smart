import groovy.sql.Sql
import java.sql.Driver
import java.text.SimpleDateFormat
import Constants;

if (Constants.SYSTEM != 'productive') {
        log.debug "Groovy Script kann nur auf dem Produktiv-System ausgeführt werden."
        return;
}

def driver = Class.forName('oracle.jdbc.OracleDriver').newInstance() as Driver

def props = new Properties()
props.setProperty("user", "sm")
props.setProperty("password", "sonsdihdf")

def conn = driver.connect("jdbc:oracle:thin:@oraprod:1521:FS4P", props)
def sql = new Sql(conn)

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()


// Teil 1: Map mit Legierungen erzeugen
String QueryLegierungen = """
{ "page": 1, "asc": 1, "objectTypeId": 83, "filters": [ { "objectTypeAttributeId": 129, "selectedValues": [ "%" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""

def HttpURLConnection connRESTLegierungen = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connRESTLegierungen.setDoOutput(true)
connRESTLegierungen.setRequestProperty("Content-Type", "application/json")
connRESTLegierungen.setRequestProperty("Authorization", "Basic ${authString}")

def wrLegierungen = new DataOutputStream(connRESTLegierungen.getOutputStream())
wrLegierungen.writeBytes(QueryLegierungen)
wrLegierungen.flush()
wrLegierungen.close()

def RESTLegierungen = new groovy.json.JsonSlurper().parse(connRESTLegierungen.getInputStream())

def MapLegierungen = new HashMap()
def String Legierungsnummer=""
def Integer IdLegierung=0

RESTLegierungen.objectEntries.each { legierung ->
    IdLegierung = legierung.id
    legierung.attributes.each {attribut ->
      if (attribut.objectTypeAttributeId==133) {
          Legierungsnummer = attribut.objectAttributeValues[0].value.toString()
      }
    }
    MapLegierungen.put (Legierungsnummer,IdLegierung)
}


def MapHerkunft = [:]
MapHerkunft.put ("Eigenguss - Guss",21717)
MapHerkunft.put ("Eigenguss - Muttercharge",21718)
MapHerkunft.put ("Teilung",21871)
MapHerkunft.put ("unbekannt / Fehler",21721)
MapHerkunft.put ("Wareneingang - Muttercharge",21719)



def AbfrageNachkommen = """
  select * from (
    select
      c.objid,
      to_char(c.addtime,'YYYY-MM') monat,
      case
        when c.materialoid is not null then (select addtime from fs4p.material mat where mat.objid=c.materialoid)
        when c.schedulableoid is not null then (select max(addtime) from fs4p.material where generatingoperationoid in (select op.objid from fs4p.schedulable op where op.batchoid=c.schedulableoid))
        else null
      end zeitstempel,
      c.chargennummer,
      mutter.jira_id muttercharge,
      (select max(herstellergussnr) from fs4p.material mat where mat.chargennr=c.chargennummer) hgnr,
      case
        when c.herkunft like '%SMCB%' then (select smcb.legnr from fs4p.schedulable smcb where smcb.objid=c.schedulableoid)
        else (select leg.apk from fs4p.composition leg where leg.objid=(select max(alloyoid) from fs4p.material mat where mat.chargennr=c.chargennummer))
      end  legnr,
      case
        when c.herkunft like '%generiert alte Logik' then 'Eigenguss - Guss'
        when c.herkunft like '%SMCB%' then 'Eigenguss - Guss'
        when c.herkunft like 'Eigenguss Platte' then 'Eigenguss - Muttercharge'
        when c.herkunft like 'Wareneingang' then 'Wareneingang - Muttercharge'
        when c.herkunft like 'Teilung%' then 'Teilung'
        else 'unbekannt / Fehler'
      end herkunft,
      c.materialoid,
      c.schedulableoid,
      c.addtime
    from sm.lab_charge c
    join sm.lab_charge mutter on mutter.objid=c.parentoid
    where mutter.jira_id is not null and
      c.jira_id is null
  ) where zeitstempel is not null
"""

def AbfrageMutterchargen = """
  select * from (
    select
      c.objid,
      to_char(c.addtime,'YYYY-MM') monat,
      case
        when c.materialoid is not null then (select addtime from fs4p.material mat where mat.objid=c.materialoid)
        when c.schedulableoid is not null then (select max(addtime) from fs4p.material where generatingoperationoid in (select op.objid from fs4p.schedulable op where op.batchoid=c.schedulableoid))
        else null
      end zeitstempel,
      c.chargennummer,
      null muttercharge,
      (select max(herstellergussnr) from fs4p.material mat where mat.chargennr=c.chargennummer) hgnr,
      case
        when c.herkunft like '%SMCB%' then (select smcb.legnr from fs4p.schedulable smcb where smcb.objid=c.schedulableoid)
        else (select leg.apk from fs4p.composition leg where leg.objid=(select max(alloyoid) from fs4p.material mat where mat.chargennr=c.chargennummer))
      end legnr,
      case
        when c.herkunft like '%generiert alte Logik' then 'Eigenguss - Guss'
        when c.herkunft like '%SMCB%' then 'Eigenguss - Guss'
        when c.herkunft like 'Eigenguss Platte' then 'Eigenguss - Muttercharge'
        when c.herkunft like 'Wareneingang' then 'Wareneingang - Muttercharge'
        when c.herkunft like 'Teilung%' then 'Teilung'
        else 'unbekannt / Fehler'
      end herkunft,
      c.materialoid,
      c.schedulableoid
    from sm.lab_charge c
    where c.parentoid is null and
      c.jira_id is null and
      c.addtime>to_date('2015-01-01','YYYY-MM-DD')
    order by c.addtime desc
  ) where rownum<11
"""

AbfrageEinzellegierungen = """
  select
    distinct leg.apk legnr
  from fs4p.SCHEDULABLE op
  join fs4p.demand d on d.objid=op.DEMANDOID
  join fs4p.WORKSTEP ws on ws.OBJID=d.WORKSTEPOID
  join fs4p.PRODUCTIONORDER fauf on fauf.objid=ws.PRODUCTIONORDEROID
  join fs4p.COMPOSITION leg on leg.objid=fauf.ALLOYOID
  where op.BATCHOID=?
"""

String statementSchluesselAblegen = """
    update sm.lab_charge set jira_id=?, jira_key=? where objid=?
"""


def rsNachkommen = sql.rows (AbfrageNachkommen)
def rsChargen = []

if (!rsNachkommen.empty) {
    log.info rsNachkommen.size().toString() + " Nachkommen gefunden"
    rsChargen=rsNachkommen
}

else {
    rsChargen=sql.rows(AbfrageMutterchargen)
    if (!rsChargen.empty) {
        log.info rsChargen.size().toString() + " neue Mutterchargen"
    }
}

def AttributeRoh = []
def objectTypeId=84
def MapObjekt = new HashMap()
def MapAttribut = new HashMap()
def MapWert = new HashMap()
def String giessanlage=""
def String gussnummer = ""
def String linkGussberichtVorlage = Constants.BASE_URL_CONFLUENCE + "/display/Giess/Gussbericht?run_1_Gussnummer=@@gussnummer@@&run_1_Anlage=@@giessanlage@@&run_1=run"
def String linkGussbericht = ""
def String linkChargenberichtVorlage = Constants.BASE_URL_CONFLUENCE + "/display/PRODWW/Chargenbericht?run_1_Chargennummer=@@chargennummer@@&run_1=run" //45297%2F2L-001
def String chargennummerURL = ""
def String linkChargenbericht = ""
def Boolean istEigenguss=false


if (!rsChargen.isEmpty()) {
    rsChargen.each {ch ->

        //log.debug ch.chargennummer.toString()

        istEigenguss=false
        if (ch.chargennummer.toString().substring(5,6)=="/") {
	  istEigenguss=true
	  giessanlage=ch.chargennummer.toString().substring(6,7)
	  gussnummer=ch.chargennummer.toString().substring(0,5)
	  linkGussbericht = linkGussberichtVorlage.replace ("@@gussnummer@@",gussnummer).replace("@@giessanlage@@",giessanlage)
	}
	chargennummerURL = ch.chargennummer.toString().replace("/","%2F")
	linkChargenbericht = linkChargenberichtVorlage.replace("@@chargennummer@@",chargennummerURL)

        def Legierungen = []

        if (ch.legnr!="*") {
            Legierungen.add(MapLegierungen.get(ch.legnr))
        }
        else {
            rsLegierungen = sql.rows(AbfrageEinzellegierungen,[ch.schedulableoid])
            rsLegierungen.each {einzelleg->
                Legierungen.add(MapLegierungen.get(einzelleg.legnr))
            }
        }

        def sdfZeitstempel = new SimpleDateFormat("dd.MM.yyyy hh:mm", Locale.ENGLISH)

        AttributeRoh = []
        AttributeRoh.add ([id:135,ListEinzelwerte:[ch.chargennummer.toString()]]) //Name -> Chargennr
        AttributeRoh.add ([id:143,ListEinzelwerte:[MapHerkunft.get(ch.herkunft)]]) //Herkunft
        AttributeRoh.add ([id:144,ListEinzelwerte:[ch.muttercharge]]) //Muttercharge
        AttributeRoh.add ([id:145,ListEinzelwerte:[ch.monat]]) //Produktionsmonat
        AttributeRoh.add ([id:146,ListEinzelwerte:Legierungen]) //Legierung
        AttributeRoh.add ([id:161,ListEinzelwerte:[sdfZeitstempel.format(ch.zeitstempel.dateValue())]]) //Zeitstempel
        AttributeRoh.add ([id:181,ListEinzelwerte:[ch.hgnr]]) //Herstellergussnr
        AttributeRoh.add ([id:761,ListEinzelwerte:[linkChargenbericht]]) //Link Chargenbericht
	if (istEigenguss==true) {
	  AttributeRoh.add ([id:762,ListEinzelwerte:[linkGussbericht]]) //Link Gussbericht
	}
        //log.debug AttributeRoh.toString()
        MapObjekt = new HashMap()
        MapAttribut = new HashMap()
        MapWert = new HashMap()
        ListAttribute = []

        MapObjekt.put ("objectTypeId",objectTypeId)
        AttributeRoh.each {attribut ->
            MapAttribut = ["objectTypeAttributeId": attribut.id]
            ListWerte = []
            attribut.ListEinzelwerte.each {einzelwert ->
                MapWert = ["value": einzelwert]
                ListWerte.add (MapWert)
            }
            MapAttribut.put ("objectAttributeValues",ListWerte)
            ListAttribute.add(MapAttribut)
        }
        MapObjekt.put ("attributes",ListAttribute)

        builder = new groovy.json.JsonBuilder (MapObjekt)
	
	//log.debug builder.toPrettyString()

        connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
        connREST.setDoOutput(true)
        connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")

        wr = new DataOutputStream(connREST.getOutputStream())
        //log.debug builder.toPrettyString()
        wr.writeBytes(builder.toPrettyString())
        wr.flush()
        wr.close()

        ChargeInsight = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
        sql.execute (statementSchluesselAblegen,[ChargeInsight.id, ChargeInsight.objectKey, ch.objid])
    }
}
else {
    log.debug "Keine neu anzulegenden Chargen gefunden."
}
