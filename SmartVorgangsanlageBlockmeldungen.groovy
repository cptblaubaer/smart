/*
KN 13.4.2016
Funktionsbeschreibung: Wir suchen uns alle Blöcke, die im Abdiendialog als "nicht o.k." bewertet wurden und zu denen
im SMART noch keine Blockmeldung angelegt ist. Die fehlenden Vorgänge werden dann über die REST-Schnittstelle erzeugt
 */

import groovy.sql.Sql
import java.sql.Driver
import java.text.DecimalFormat
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

def sdfZeitstempel = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

def dfGanzahlig = new DecimalFormat("0")

def MapVorgang = new HashMap()
def MapFields = new HashMap()
def MapFieldcontent = new HashMap()
def ListItems = new ArrayList()
def MapObjekt = new HashMap()

def slurper = new groovy.json.JsonSlurper()

def abfrageArbeitsvorrat = """
  select
     mat.objid materialoid,
     mat.CHARGENNR,
     mat.gussnr,
     mat.giessanlagennr,
    (select kommentar.value from fs4p.smactualvalue kommentar where kommentar.key like '862%-Abdienen-Bemerkung' and kommentar.materialoid=mat.objid) kommentar,
    (select max(imposationcomment) from fs4p.locktbl lck where lck.materialoid=mat.objid and lck.typeoid='40288a9507e684d10107e68fcd170576') werkersperre,
     lc.JIRA_ID,
     lc.jira_key
  from fs4p.material mat
  join fs4p.schedulable genop on genop.OBJID=mat.GENERATINGOPERATIONOID
  join fs4p.SCHEDULABLE smcb on smcb.OBJID=genop.BATCHOID
  join fs4p.SMACTUALVALUE bewertung on bewertung.MATERIALOID=mat.objid
  join sm.LAB_CHARGE lc on lc.CHARGENNUMMER=mat.CHARGENNR
  where smcb.DISCRIMINATOR='SMCASTINGBATCH' and
    bewertung.key like '862%-Abdienen-Qualitaetsbewertung' and
    bewertung.value='Nicht OK' and
    mat.GENERATIONSTATE=400 and
    mat.ADDTIME>to_date('2016-01-01','YYYY-MM-DD') and
    lc.JIRA_ID is not null and
    not exists (select * from sm.giess_blockmeldungen bm where bm.chargennummer=mat.chargennr) and
    rownum<2
  order by mat.addtime
"""

def statementEinfuegenBlockmeldungDatenbank = """
  insert into sm.GIESS_BLOCKMELDUNGEN (materialoid, CHARGENNUMMER, JIRA_ID, JIRA_KEY) values (?,?,?,?)
"""

def AbfrageBlockmeldungVorhandenVorlage = """
{
    "jql": "project = PM AND issuetype = Blockbeurteilung AND Chargen = @@jira_key_charge@@",
    "startAt": 0,
    "maxResults": 9999,
    "fields": [
      "issuekey"
    ]
}
"""

def AbfrageBlockmeldungVorhanden = ""

HttpURLConnection connSearch = new URL(Constants.BASE_URL_SMART + "/rest/api/2/search/").openConnection()
connSearch.setDoOutput(true)
connSearch.setRequestProperty("Content-Type", "application/json")
connSearch.setRequestProperty("Authorization", "Basic ${authString}")
wrSearch = new DataOutputStream(connSearch.getOutputStream())

HttpURLConnection connCreate = new URL(Constants.BASE_URL_SMART + "/rest/api/2/issue/").openConnection()
connCreate.setDoOutput(true)
connCreate.setRequestProperty("Content-Type", "application/json")
connCreate.setRequestProperty("Authorization", "Basic ${authString}")
wrCreate = new DataOutputStream(connCreate.getOutputStream())


def rsArbeitsvorrat = sql.rows(abfrageArbeitsvorrat)

if (rsArbeitsvorrat.isEmpty()==false) {
    rsArbeitsvorrat.each { block ->
        //log.debug block.toString()
        AbfrageBlockmeldungVorhanden = AbfrageBlockmeldungVorhandenVorlage.replace("@@jira_key_charge@@",block.jira_key) //block.jira_key)

        //log.debug AbfrageBlockmeldungVorhanden

        wrSearch.writeBytes(AbfrageBlockmeldungVorhanden)
        wrSearch.flush()

        def SearchResult = new groovy.json.JsonSlurper().parse(connSearch.getInputStream())

        int anzahlBlockmeldungenZurCharge = SearchResult.total.intValue()

        log.debug anzahlBlockmeldungenZurCharge.toString() + " Blockmeldungen gefunden"

        if (anzahlBlockmeldungenZurCharge==0) {
            MapFieldcontent.put ("key","PM")
            MapFields.put ("project",MapFieldcontent)
            MapFieldcontent = new HashMap()
            MapFieldcontent.put ("name","Blockbeurteilung")
            MapFields.put("issuetype",MapFieldcontent)
            MapFields.put("summary","Neu") //wird durch workflow ersetzt
            // Produkt Materialbewertung: cf_10519
            MapObjekt.put ("key","SM-107871") //immer auf Default "00 - unbewertet" setzen
            ListItems.add(MapObjekt)
            MapFields.put ("customfield_10519",ListItems)
            // Chargen: cf_10200
            MapObjekt = new HashMap()
            ListItems = new ArrayList()
            MapObjekt.put ("key",block.jira_key)
            ListItems.add(MapObjekt)
            MapFields.put ("customfield_10200",ListItems)
            // BDE Sperrgrund Werkersperre: cf_10804
            MapFields.put ("customfield_10804",(block.werkersperre==null?"":block.werkersperre))
            // BDE Bemerkung Blockbewertung: cf_10803
            MapFields.put ("customfield_10803",(block.kommentar==null?"":block.kommentar))
            MapVorgang.put ("fields",MapFields)

            builder = new groovy.json.JsonBuilder (MapVorgang)

            //log.debug builder.toPrettyString()

            wrCreate.writeBytes(builder.toPrettyString())
            wrCreate.flush()

            try {response=connCreate.getInputStream()}
            catch (java.io.IOException e) {}
            log.debug slurper.parse(response)
        }
        else {
            // Wenn es schon eine Blockmeldung gibt: Ablage in Tabelle sm.GIESS_BLOCKMELDUNGEN
            log.debug "Datenbank aktualisiert"
            sql.execute(statementEinfuegenBlockmeldungDatenbank,[block.materialoid, block.chargennr,SearchResult.issues[0].id,SearchResult.issues[0].key ])
        }

    }
    wrSearch.close()
    wrCreate.close()
}
else {
    log.debug "Keine anzulegenden Blockmeldungen gefunden."
}