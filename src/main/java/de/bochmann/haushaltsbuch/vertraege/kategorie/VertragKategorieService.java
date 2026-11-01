package de.bochmann.haushaltsbuch.vertraege.kategorie;

import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kategorien, die der aktuelle Haushalt verwenden darf: die Standardkategorien und (ab #34) seine
 * eigenen. Kategorien anderer Haushalte gelten als unbekannt.
 */
@Service
@Transactional(readOnly = true)
public class VertragKategorieService {

	private final VertragKategorieRepository repository;

	private final HaushaltKontext haushaltKontext;

	VertragKategorieService(VertragKategorieRepository repository, HaushaltKontext haushaltKontext) {
		this.repository = repository;
		this.haushaltKontext = haushaltKontext;
	}

	public List<VertragKategorie> verwendbare() {
		return repository.findVerwendbare(haushalt());
	}

	public Optional<VertragKategorie> verwendbare(UUID id) {
		return repository.findVerwendbare(id, haushalt());
	}

	private UUID haushalt() {
		return haushaltKontext.id().orElse(HaushaltKontext.KEIN_HAUSHALT);
	}

}
