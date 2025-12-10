package com.medblocks.openfhir.ips;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Condition;
import org.junit.Test;

public class IpsTest extends GenericTest {

    final String MODEL_MAPPINGS = "/ips/";
    final String CONTEXT_MAPPING = "/ips/problem_list.context.yml";
    final String HELPER_LOCATION = "/ips/";
    final String OPT = "Problem List NL.opt";


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }


    @Test
    public void toOpenEhrToFhir() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + "ips.flat.json"),
                new OPTParser(operationaltemplate).parse());

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);

        // Assert bundle metadata
        assertNotNull("Bundle should not be null", bundle);
        assertEquals("Bundle type should be collection", Bundle.BundleType.COLLECTION, bundle.getType());
        assertNotNull("Bundle should have entries", bundle.getEntry());
        assertEquals("Bundle should have 3 entries", 2, bundle.getEntry().size());

        // Assert first entry is a Composition
        final BundleEntryComponent firstEntry = bundle.getEntry().get(0);
        assertNotNull("First entry should not be null", firstEntry);
        assertTrue("First entry should be a Composition",
                   firstEntry.getResource() instanceof org.hl7.fhir.r4.model.Composition);

        final org.hl7.fhir.r4.model.Composition compositionResource =
                (org.hl7.fhir.r4.model.Composition) firstEntry.getResource();

        // Assert Composition metadata
        assertNotNull("Composition should have a date", compositionResource.getDate());
        assertNotNull("Composition should have sections", compositionResource.getSection());
        assertFalse("Composition should have at least one section", compositionResource.getSection().isEmpty());

        // Assert Composition section details
        final org.hl7.fhir.r4.model.Composition.SectionComponent section = compositionResource.getSectionFirstRep();
        assertEquals("Section title should be 'Active Problems'", "Active Problems", section.getTitle());
        assertNotNull("Section should have a code", section.getCode());
        assertNotNull("Section should have text", section.getText());
        assertEquals("Section text status should be 'generated'", "generated", section.getText().getStatusElement().getValueAsString());

        // Assert section code (LOINC)
        assertFalse("Section code should have codings", section.getCode().getCoding().isEmpty());
        assertEquals("Section code system should be LOINC",
                     "http://loinc.org",
                     section.getCode().getCodingFirstRep().getSystem());
        assertEquals("Section code should be '11450-4'",
                     "11450-4",
                     section.getCode().getCodingFirstRep().getCode());
        assertEquals("Section code display should be 'Problem list Reported'",
                     "Problem list Reported",
                     section.getCode().getCodingFirstRep().getDisplay());

        // Assert Composition.section.entry points to Condition
        assertNotNull("Section should have entries", section.getEntry());
        assertFalse("Section should have at least one entry", section.getEntry().isEmpty());

        final Condition condition = (Condition) compositionResource.getSectionFirstRep().getEntryFirstRep().getResource();

        // Assert Condition properties
        assertNotNull("Condition should not be null", condition);
        assertNotNull("Condition should have text", condition.getText());
        assertEquals("Condition text status should be 'generated'",
                     "generated",
                     condition.getText().getStatusElement().getValueAsString());

        // Assert clinical status
        assertNotNull("Condition should have clinicalStatus", condition.getClinicalStatus());
        assertFalse("Condition clinicalStatus should have codings",
                    condition.getClinicalStatus().getCoding().isEmpty());

        // Assert verification status
        assertNotNull("Condition should have verificationStatus", condition.getVerificationStatus());
        assertFalse("Condition verificationStatus should have codings",
                    condition.getVerificationStatus().getCoding().isEmpty());

        // Assert severity
        assertNotNull("Condition should have severity", condition.getSeverity());
        assertFalse("Condition severity should have codings",
                    condition.getSeverity().getCoding().isEmpty());

        // Assert code (diagnosis)
        assertNotNull("Condition should have a code", condition.getCode());
        assertFalse("Condition code should have codings", condition.getCode().getCoding().isEmpty());
        assertEquals("Condition code system should be SNOMED CT",
                     "http://snomed.info/sct",
                     condition.getCode().getCodingFirstRep().getSystem());
        assertEquals("Condition code should be '709044004'",
                     "709044004",
                     condition.getCode().getCodingFirstRep().getCode());
        assertEquals("Condition code display should be 'Chronic Kidney Disease'",
                     "Chronic Kidney Disease",
                     condition.getCode().getCodingFirstRep().getDisplay());

        // Assert body site
        assertNotNull("Condition should have bodySite", condition.getBodySite());
        assertFalse("Condition should have at least one bodySite", condition.getBodySite().isEmpty());
        assertFalse("Condition bodySite should have codings",
                    condition.getBodySiteFirstRep().getCoding().isEmpty());
        assertEquals("Body site system should be SNOMED CT",
                     "http://snomed.info/sct",
                     condition.getBodySiteFirstRep().getCodingFirstRep().getSystem());

        // Assert dates
        assertNotNull("Condition should have onsetDateTime", condition.getOnsetDateTimeType());
        assertNotNull("Condition should have recordedDate", condition.getRecordedDate());

        // Assert notes
        assertNotNull("Condition should have notes", condition.getNote());
        assertFalse("Condition should have at least one note", condition.getNote().isEmpty());

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, bundle, operationaltemplate);

    }


}
