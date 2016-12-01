package de.schwermetall.smart

import static org.junit.Assert.*
import groovy.transform.ToString

import org.junit.After
import org.junit.Before
import org.junit.Test

import de.msw.api.POIFactoryComponent
import de.msw.impl.POIFactoryComponentImpl;
import de.msw.model.Row
import de.msw.model.Sheet
import de.msw.model.Workbook

class SimplePoiTester
{
	POIFactoryComponent poiFactoryComponent

	@ToString
	class TREintrag
	{
		Integer nummer
		Integer excelzeile
		Date datum
		String schicht
		String vorfall
		String handwerkerRoh
		ArrayList personenNamen
		ArrayList personenSchluessel
		String massnahme
		Date startzeit
		Date endzeit
		String adduserRoh
		String einflussIH
		String einflussVT
		String verweis
		String strukturRoh
		ArrayList strukturObjekte
		String vorgangstyp
		String status
		Integer fehlerStatus
	}

	@Before
	public void setUp() throws Exception
	{
		poiFactoryComponent = new POIFactoryComponentImpl()
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void test()
	{

		Workbook workbook

		TREintrag trEintrag
		Date eintragDatum
		Row row
		def TREintraege = new ArrayList()

		try
		{
			workbook = poiFactoryComponent.loadSMBWorkbook("smb://verw07/Stoerungsmeldungen/Notizen f\u00FCrTechnikreport Gießerei\u002C Metalllager.xlsx", "gi", "provis");
			Sheet sheet = workbook.getSheetAt(0)

			for (Zeile in (4254..10000))
			{
				//System.out.println( (Zeile+1).toString() )
				row = sheet.getRow(Zeile)
				eintragDatum=row.getCell(1)?.getDateCellValue()
				if( eintragDatum == null ){ //Wenn wir eine Zeile ohne Datum finden, haben wir das Ende erreicht
					break
				}

				trEintrag = new TREintrag()
				trEintrag.setStatus("Neu")
				trEintrag.setFehlerStatus(0)
				trEintrag.setPersonenNamen ([] as ArrayList)
				trEintrag.setPersonenSchluessel ([] as ArrayList)
				trEintrag.setExcelzeile(Zeile+1)
				if (row.getCell(0)?.getCellType()==0) {
					trEintrag.setNummer(row.getCell(0).getNumericCellValue().intValue())
				}
				if (row.getCell(1)?.getCellType()==0)
				{
					trEintrag.setDatum(row.getCell(1).getDateCellValue())
				}
				if (row.getCell(2)?.getCellType()==1) {
					trEintrag.setSchicht(row.getCell(2).getStringCellValue())
				}
				if (row.getCell(4)?.getCellType()==1) {
					trEintrag.setVorfall(row.getCell(4).getStringCellValue())
				}
				if (row.getCell(7)?.getCellType()==1) {
					trEintrag.setHandwerkerRoh(row.getCell(7).getStringCellValue())
				}
				if (row.getCell(8)?.getCellType()==1) {
					trEintrag.setMassnahme(row.getCell(8).getStringCellValue())
				}
				if (row.getCell(9)?.getCellType()==0) {
					trEintrag.setStartzeit(row.getCell(9).getDateCellValue())
				}
				if (row.getCell(10)?.getCellType()==0) {
					trEintrag.setEndzeit(row.getCell(10).getDateCellValue())
				}
				if (row.getCell(11)?.getCellType()==1) {
					trEintrag.setAdduserRoh(row.getCell(11).getStringCellValue())
				}
				if (row.getCell(15)?.getCellType()==1) {
					trEintrag.setEinflussIH(row.getCell(15).getStringCellValue())
				}
				if (row.getCell(16)?.getCellType()==1) {
					trEintrag.setEinflussVT(row.getCell(16).getStringCellValue())
				}
				if (row.getCell(17)?.getCellType()==1) {
					trEintrag.setVerweis(row.getCell(17)?.getStringCellValue())
				}
				if (row.getCell(18)?.getCellType()==0) {
					trEintrag.setStrukturRoh(row.getCell(18)?.getNumericCellValue().toString())
				}
				if (row.getCell(18)?.getCellType()==1) {
					trEintrag.setStrukturRoh(row.getCell(18)?.getStringCellValue())
				}
				if (row.getCell(19)?.getCellType()==1) {
					trEintrag.setVorgangstyp(row.getCell(19)?.getStringCellValue())
				}
				TREintraege.add(trEintrag )
			}
		}
		finally
		{
			workbook?.close()
		}
		System.out.println "Vorgänge gelesen: " + TREintraege.size()
		//Kein Vorgangstyp angegeben: Fehler
		def Integer zaehler=0
		TREintraege.each {TREintrag eintrag ->
			if (eintrag.getFehlerStatus()==0) {
				if (eintrag.getVorgangstyp()==null) {
					eintrag.setStatus("Kein Vorgangstyp angegeben")
					eintrag.setFehlerStatus(1)
					zaehler = zaehler+1
					System.out.println eintrag.getExcelzeile()
				}
			}
		}
		System.out.println "Ohne Vorgangstyp: " + zaehler.toString()
		//Vorgänge vom Vorgangstyp "verwerfen" entfernen
		zaehler=0
		ArrayList listLoeschkandidaten = []
		TREintraege.each{TREintrag eintrag ->
			if (eintrag.getVorgangstyp()=="verwerfen") {
				listLoeschkandidaten.add(eintrag)
				zaehler = zaehler+1
			}
		}
		listLoeschkandidaten.each{TREintrag kandidat->
			TREintraege.remove(kandidat)
		}
		System.out.println "verworfen: " + zaehler.toString()
		// Wenn keine Struktur angegeben: Fehler
		zaehler=0
		TREintraege.each {TREintrag eintrag ->
			if (eintrag.getStrukturRoh()==null) {
				eintrag.setStatus("Keine Struktur angegeben")
				eintrag.setFehlerStatus(1)
				zaehler = zaehler+1
			}
		}
		System.out.println "Ohne Struktur: " + zaehler.toString()
		// Wenn kein Vorgangstyp angegeben: Fehler
		zaehler=0
		// Personennamen parsen
		def ArrayList ListeIgnorieren = ["Datenbaustein durch Fa.Dohmen gewechselt","???","Anlagenbediener","keiner","Dachdecker","Fa. Huberty","?","Externe Leute", "Bediener","Stühlen: Krings, Czabainka, Külik, Markowski","Firma Coban","Vaßen Schmiede","Stühlen: Matoussi, Mevis, Steckenborn und ein weiterer","Miebach","Mannschaft GA2","Fa. Nehles","Schicht 4","Nehles","Fa.Stühlen","Fa.Nehles","Fa. HPS","Fa. Stühlen","Fa Stühlen","Fa.Coban","Fa. Coban","Roger NL","Fa. Wolff","Keulen","Fa. Wieland","Fa. Schönen","Fa. Atil","Nehles, Klüttgens","Rheinkalk","HSV","Stühlen","Mathars Belgier","Fa. Frings"]
		def HashMap MapPersonen = [:]
		MapPersonen.put("Peper", "SM-114150")
		MapPersonen.put("Hennecken", "SM-114137")
		MapPersonen.put("Groß", "SM-114038")
		MapPersonen.put("Pickart", "SM-113922")
		MapPersonen.put("Kaulen", "SM-113917")
		MapPersonen.put("Durak", "SM-114263")
		MapPersonen.put("Stoll", "SM-114219")
		MapPersonen.put("Bernard", "SM-114086")
		MapPersonen.put("Wirtz", "SM-106412")
		MapPersonen.put("Müller S.", "SM-114165")
		MapPersonen.put("Cubukcu", "SM-114095")
		MapPersonen.put("Dederichs", "SM-114196")
		MapPersonen.put("Neumann", "SM-105079")
		MapPersonen.put("Kayku", "SM-114214")
		MapPersonen.put("Ziemons", "SM-114161")
		MapPersonen.put("Jakobs", "SM-114122")
		MapPersonen.put("Pompejus", "SM-114051")
		MapPersonen.put("Call", "SM-106418")
		MapPersonen.put("von Högen", "SM-114061")
		MapPersonen.put("Mirke", "SM-114035")
		MapPersonen.put("Kösters", "SM-114075")
		MapPersonen.put("Freyaldenhoven", "SM-114030")
		MapPersonen.put("Holz", "SM-105099")
		MapPersonen.put("Nehles (Winkler)", "SM-134548")
		MapPersonen.put("Peters", "SM-114164")
		MapPersonen.put("Kollath", "SM-114258")
		MapPersonen.put("Ertürk", "SM-106425")
		MapPersonen.put("E. Holz", "SM-105099")
		MapPersonen.put("Coban", "SM-106413")
		MapPersonen.put("T.Rotheut", "SM-114384")
		MapPersonen.put("Schlepütz", "SM-113947")
		MapPersonen.put("Bär", "SM-114064")
		MapPersonen.put("Lo-Cicero", "SM-114089")
		MapPersonen.put("Leisten", "SM-114111")
		MapPersonen.put("Lo Cicero", "SM-114089")
		MapPersonen.put("Engels", "SM-123012")
		MapPersonen.put("Appel", "SM-113965")
		MapPersonen.put("Focker", "SM-114109")
		MapPersonen.put("Schal", "SM-114169")
		MapPersonen.put("Meulenberg", "SM-114100")
		MapPersonen.put("Röntgen", "SM-114133")
		MapPersonen.put("Ertürk M.", "SM-106425")
		MapPersonen.put("Martinez", "SM-113970")
		MapPersonen.put("Dinc", "SM-113924")
		MapPersonen.put("Stoll R.", "SM-114189")
		MapPersonen.put("Akyüz", "SM-113921")
		MapPersonen.put("Zylus", "SM-114065")
		MapPersonen.put("Erberich", "SM-114029")
		MapPersonen.put("Stoll Robert", "SM-114189")
		MapPersonen.put("R. Stoll", "SM-114189")
		MapPersonen.put("Wirtz P.", "SM-113989")
		MapPersonen.put("Sarin L.", "SM-113988")
		MapPersonen.put("Flocken", "SM-114144")
		MapPersonen.put("Schröder", "SM-114178")
		MapPersonen.put("Morosow", "SM-114081")
		MapPersonen.put("F. J. Hoffmann", "SM-114213")
		MapPersonen.put("Willems", "SM-114264")
		MapPersonen.put("Faust", "SM-114031")
		MapPersonen.put("Teutenberg", "SM-106419")
		MapPersonen.put("R.Stoll", "SM-114189")
		MapPersonen.put("Stoll A.", "SM-114219")
		MapPersonen.put("A. Stoll", "SM-114219")
		MapPersonen.put("Kayku O.", "SM-114214")
		MapPersonen.put("Jakobs M.", "SM-134555")
		MapPersonen.put("I.Schlepütz", "SM-113947")
		MapPersonen.put("Coban Ö", "SM-106413")
		MapPersonen.put("Hoffmann", "SM-114213")
		MapPersonen.put("Schicka", "SM-114135")
		MapPersonen.put("Dentzer", "SM-114077")
		MapPersonen.put("Greiser C.", "SM-114222")
		MapPersonen.put("Coban Ö.", "SM-106413")
		MapPersonen.put("Schal?", "SM-114169")
		MapPersonen.put("Bünten", "SM-106410")
		MapPersonen.put("Fuhrmeister", "SM-114253")
		MapPersonen.put("Dörr", "SM-113963")
		MapPersonen.put("Pikart", "SM-113922")
		MapPersonen.put("Linzenich", "SM-113935")
		MapPersonen.put("Brodmühler", "SM-114185")
		MapPersonen.put("Mathar", "SM-113934")
		MapPersonen.put("Kelling", "SM-114204")
		MapPersonen.put("Mirik", "SM-114116")
		MapPersonen.put("R.Schröder", "SM-114178")
		MapPersonen.put("Coban Ömer", "SM-106413")
		MapPersonen.put("U.Peters", "SM-114164")
		MapPersonen.put("Schewe", "SM-114236")
		MapPersonen.put("Von Högen", "SM-114061")
		MapPersonen.put("Gärtner", "SM-114097")
		MapPersonen.put("Zakowski", "SM-114076")
		MapPersonen.put("von  Högen", "SM-114061")
		MapPersonen.put("Alkan", "SM-114096")
		MapPersonen.put("Cardenas", "SM-113948")
		MapPersonen.put("", "")
		MapPersonen.put("", "")
		TREintraege.each {TREintrag eintrag ->
			if (eintrag.handwerkerRoh?.length()>0) {
				eintrag.handwerkerRoh.split("/").each {handwerker ->
					if (eintrag.personenNamen.contains(handwerker.trim())==false) {
						eintrag.personenNamen.add (handwerker.trim())
					}
				}
			}
			if (eintrag.adduserRoh?.length()>0) {
				eintrag.adduserRoh.split("/").each {adduser ->
					if (eintrag.personenNamen.contains(adduser.trim())==false) {
						eintrag.personenNamen.add (adduser.trim())
					}
				}
			}
			eintrag.personenNamen.each {personName ->
				if (ListeIgnorieren.contains(personName)==false) {
					if (MapPersonen.get(personName)!=null) {
						if (eintrag.personenSchluessel.contains(MapPersonen.get(personName))==false) {
							eintrag.personenSchluessel.add(MapPersonen.get(personName))
						}
					}
					else {
						String meldung = "Name " + personName + " nicht zu interpretieren"
						eintrag.setStatus(meldung)
						eintrag.setFehlerStatus(1)
						System.out.println (eintrag.getExcelzeile().toString() + " - " + meldung)
					}
				}
			}
		}

		//Bezugsobjekte holen


		// Einträge mit Fehlerstatus 0 zählen
		zaehler=0
		TREintraege.each {TREintrag eintrag ->
			if (eintrag.getFehlerStatus()==0) {
				zaehler = zaehler+1
			}
		}
		System.out.println "Fehlerstatus 0: " + zaehler.toString()
		System.out.println "Zeilen gesamt: " + TREintraege.size().toString()

	}
}
