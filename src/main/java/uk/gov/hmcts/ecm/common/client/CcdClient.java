package uk.gov.hmcts.ecm.common.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ecm.common.helpers.ESHelper;
import uk.gov.hmcts.ecm.common.model.bulk.BulkCaseSearchResult;
import uk.gov.hmcts.ecm.common.model.bulk.BulkData;
import uk.gov.hmcts.ecm.common.model.bulk.SubmitBulkEvent;
import uk.gov.hmcts.ecm.common.model.ccd.*;
import uk.gov.hmcts.ecm.common.model.labels.LabelCaseSearchResult;
import uk.gov.hmcts.ecm.common.model.labels.LabelPayloadEvent;
import uk.gov.hmcts.ecm.common.model.multiples.MultipleCaseSearchResult;
import uk.gov.hmcts.ecm.common.model.multiples.MultipleData;
import uk.gov.hmcts.ecm.common.model.multiples.SubmitMultipleEvent;
import uk.gov.hmcts.ecm.common.model.reference.ReferenceSubmitEvent;
import uk.gov.hmcts.ecm.common.model.schedule.ScheduleCaseSearchResult;
import uk.gov.hmcts.ecm.common.model.schedule.SchedulePayloadEvent;
import uk.gov.hmcts.ecm.common.service.UserService;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.*;

@Slf4j
public class CcdClient {

    private transient RestTemplate restTemplate;
    private transient UserService userService;
    private transient CcdClientConfig ccdClientConfig;
    private transient CaseDataBuilder caseDataBuilder;
    private transient AuthTokenGenerator authTokenGenerator;

    private static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    static final String CREATION_EVENT_SUMMARY = "Case created automatically";
    private static final String UPDATE_EVENT_SUMMARY = "Case updated by bulk";
    static final String UPDATE_BULK_EVENT_SUMMARY = "Bulk case updated by bulk";
    private static final int MAX_RETRIES = 7;

    public CcdClient(RestTemplate restTemplate, UserService userService, CaseDataBuilder caseDataBuilder,
                     CcdClientConfig ccdClientConfig, AuthTokenGenerator authTokenGenerator) {
        this.restTemplate = restTemplate;
        this.userService = userService;
        this.ccdClientConfig = ccdClientConfig;
        this.authTokenGenerator = authTokenGenerator;
        this.caseDataBuilder = caseDataBuilder;
    }

    public CCDRequest startCaseCreation(String authToken, CaseDetails caseDetails) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartCaseCreationUrl(userService.getUserDetails(authToken).getUid(), caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId());
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startCaseCreationTransfer(String authToken, CaseDetails caseDetails) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartCaseCreationTransferUrl(userService.getUserDetails(authToken).getUid(), caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId());
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startCaseTransfer(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartCaseTransferUrl(userService.getUserDetails(authToken).getUid(),
                jurisdiction, caseTypeId);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest returnCaseCreationTransfer(String authToken, String caseTypeId, String jurisdiction)
            throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildReturnCaseCreationTransferUrl(userService.getUserDetails(authToken).getUid(),
                jurisdiction, caseTypeId);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startCaseMultipleCreation(String authToken, String caseTypeId, String jurisdiction)
            throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartCaseMultipleCreationUrl(userService.getUserDetails(authToken).getUid(),
                jurisdiction, caseTypeId);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public SubmitEvent submitCaseCreation(String authToken, CaseDetails caseDetails, CCDRequest req) throws IOException {
        HttpEntity<CaseDataContent> request =
                new HttpEntity<>(caseDataBuilder.buildCaseDataContent(caseDetails.getCaseData(), req, CREATION_EVENT_SUMMARY), buildHeaders(authToken));
        String uri = ccdClientConfig.buildSubmitCaseCreationUrl(userService.getUserDetails(authToken).getUid(), caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId());
        return restTemplate.exchange(uri, HttpMethod.POST, request, SubmitEvent.class).getBody();
    }

    public SubmitEvent retrieveCase(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<CCDRequest> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildRetrieveCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, SubmitEvent.class).getBody();
    }

    private PaginatedSearchMetadata searchMetadata(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        HttpEntity<CCDRequest> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildPaginationMetadataCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId);
        return restTemplate.exchange(uri, HttpMethod.GET, request, PaginatedSearchMetadata.class).getBody();
    }

    private String getURI(String authToken, String caseTypeId, String jurisdiction, String page) {
        return ccdClientConfig.buildRetrieveCasesUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, page);
    }

    private int getTotalPagesCount(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        PaginatedSearchMetadata paginatedSearchMetadata = searchMetadata(authToken, caseTypeId, jurisdiction);
        return paginatedSearchMetadata.getTotalPagesCount();
    }

    public List<SubmitEvent> retrieveCases(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        List<SubmitEvent> submitEvents = new ArrayList<>();
        for (int page = 1; page <= getTotalPagesCount(authToken, caseTypeId, jurisdiction); page++) {
            List<SubmitEvent> submitEventAux = restTemplate.exchange(ccdClientConfig.buildRetrieveCasesUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, String.valueOf(page)), HttpMethod.GET, new HttpEntity<>(buildHeaders(authToken)), new ParameterizedTypeReference<List<SubmitEvent>>(){}).getBody();
            if (submitEventAux != null) {
                submitEvents.addAll(submitEventAux);
            }
        }
        return submitEvents;
    }

    public List<ReferenceSubmitEvent> retrieveReferenceDataCases(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        List<ReferenceSubmitEvent> referenceSubmitEvents = new ArrayList<>();
        for (int page = 1; page <= getTotalPagesCount(authToken, caseTypeId, jurisdiction); page++) {
            List<ReferenceSubmitEvent> submitEventAux = restTemplate.exchange(ccdClientConfig.buildRetrieveCasesUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                    caseTypeId, String.valueOf(page)), HttpMethod.GET, new HttpEntity<>(buildHeaders(authToken)), new ParameterizedTypeReference<List<ReferenceSubmitEvent>>(){}).getBody();
            if (submitEventAux != null) {
                referenceSubmitEvents.addAll(submitEventAux);
            }
        }
        return referenceSubmitEvents;
    }

    private String getListingQuery(String from, String to, String venue, String mapping) {
        if (ALL_VENUES.equals(venue)) {
            log.info("QUERY DATE: " + ESHelper.getListingRangeDateSearchQuery(from, to));
            return ESHelper.getListingRangeDateSearchQuery(from, to);
        } else {
            log.info("QUERY DATE + VENUE: " + ESHelper.getListingVenueAndRangeDateSearchQuery(from, to, venue, mapping));
            return ESHelper.getListingVenueAndRangeDateSearchQuery(from, to, venue, mapping);
        }
    }

    public List<SubmitEvent> retrieveCasesVenueAndDateElasticSearch(String authToken, String caseTypeId, String dateToSearchFrom, String dateToSearchTo,
                                                                 String venueToSearch, String venueToSearchMapping) throws IOException {
        String from = LocalDate.parse(dateToSearchFrom).atStartOfDay().format(OLD_DATE_TIME_PATTERN);
        if (dateToSearchTo.equals(dateToSearchFrom)) {
            String to = LocalDate.parse(dateToSearchFrom).atStartOfDay().plusDays(1).minusSeconds(1).format(OLD_DATE_TIME_PATTERN);
            return buildAndGetElasticSearchRequest(authToken, caseTypeId,
                    getListingQuery(from, to, venueToSearch, venueToSearchMapping));
        } else {
            String to = LocalDate.parse(dateToSearchTo).atStartOfDay().format(OLD_DATE_TIME_PATTERN);
            return buildAndGetElasticSearchRequest(authToken, caseTypeId,
                    getListingQuery(from, to, venueToSearch, venueToSearchMapping));
        }
    }

    public List<SubmitEvent> retrieveCasesGenericReportElasticSearch(String authToken, String caseTypeId, String dateToSearchFrom, String dateToSearchTo,
                                                                    String reportType) throws IOException {
        String from = LocalDate.parse(dateToSearchFrom).atStartOfDay().format(OLD_DATE_TIME_PATTERN);
        if (dateToSearchTo.equals(dateToSearchFrom)) {
            String to = LocalDate.parse(dateToSearchFrom).atStartOfDay().plusDays(1).minusSeconds(1).format(OLD_DATE_TIME_PATTERN);
            log.info(reportType + " - " + from + " - " + to);
            return buildAndGetElasticSearchRequest(authToken, caseTypeId,
                    getReportRangeDateQuery(from, to, reportType));
        } else {
            String to = LocalDate.parse(dateToSearchTo).atStartOfDay().format(OLD_DATE_TIME_PATTERN);
            log.info(reportType + " - " + from + " - " + to);
            return buildAndGetElasticSearchRequest(authToken, caseTypeId,
                    getReportRangeDateQuery(from, to, reportType));
        }
    }

    private String getReportRangeDateQuery(String from, String to, String reportType) {
        log.info("REPORT QUERY DATE: " + ESHelper.getReportRangeDateSearchQuery(from, to, reportType));
        return ESHelper.getReportRangeDateSearchQuery(from, to, reportType);
    }

    private List<SubmitEvent> buildAndGetElasticSearchRequest(String authToken, String caseTypeId, String query) throws IOException {
        List<SubmitEvent> submitEvents = new ArrayList<>();
        HttpEntity<String> request = new HttpEntity<>(query, buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        CaseSearchResult caseSearchResult = restTemplate.exchange(url, HttpMethod.POST, request, CaseSearchResult.class).getBody();
        if (caseSearchResult != null && caseSearchResult.getCases() != null) {
            submitEvents.addAll(caseSearchResult.getCases());
        }
        return submitEvents;
    }

    private List<SchedulePayloadEvent> buildAndGetElasticSearchScheduleRequest(String authToken, String caseTypeId, String query) throws IOException {
        List<SchedulePayloadEvent> schedulePayloadEvents = new ArrayList<>();
        HttpEntity<String> request = new HttpEntity<>(query, buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        ScheduleCaseSearchResult scheduleCaseSearchResult =
                restTemplate.exchange(url, HttpMethod.POST, request, ScheduleCaseSearchResult.class).getBody();
        if (scheduleCaseSearchResult != null && scheduleCaseSearchResult.getCases() != null) {
            schedulePayloadEvents.addAll(scheduleCaseSearchResult.getCases());
        }
        //log.info("ScheduleTest: " + restTemplate.exchange(url, HttpMethod.POST, request, String.class).getBody());
        return schedulePayloadEvents;
    }

    private List<LabelPayloadEvent> buildAndGetElasticSearchLabelRequest(String authToken, String caseTypeId, String query) throws IOException {
        List<LabelPayloadEvent> labelPayloadEvents = new ArrayList<>();
        HttpEntity<String> request = new HttpEntity<>(query, buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        LabelCaseSearchResult labelCaseSearchResult =
                restTemplate.exchange(url, HttpMethod.POST, request, LabelCaseSearchResult.class).getBody();
        if (labelCaseSearchResult != null && labelCaseSearchResult.getCases() != null) {
            labelPayloadEvents.addAll(labelCaseSearchResult.getCases());
        }
        return labelPayloadEvents;
    }

    public List<SubmitMultipleEvent> buildAndGetElasticSearchRequestWithRetriesMultiples(String authToken, String caseTypeId, String query) throws IOException {
        HttpEntity<String> request = new HttpEntity<>(query, buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        MultipleCaseSearchResult multipleCaseSearchResult;
        int retries = 1;
        do {
            log.info("Retry: " + retries);
            multipleCaseSearchResult = restTemplate.exchange(url, HttpMethod.POST, request, MultipleCaseSearchResult.class).getBody();
            try {
                if (multipleCaseSearchResult != null) {
                    log.info("Checking size found: " + multipleCaseSearchResult.getTotal());
                }
                TimeUnit.SECONDS.sleep(5);
                retries++;
                if (retries == MAX_RETRIES) {
                    break;
                }
            } catch (InterruptedException e) {
                log.error("Error sleeping the thread");
                Thread.currentThread().interrupt();
            }
        } while (multipleCaseSearchResult == null || multipleCaseSearchResult.getTotal() != 1);

        return multipleCaseSearchResult != null
                ? new ArrayList<>(multipleCaseSearchResult.getCases())
                : new ArrayList<>();

    }

    private List<SubmitEvent> buildAndGetElasticSearchRequestWithRetries(String authToken, String caseTypeId, String query, int size, List<String> caseIds) throws IOException {
        HttpEntity<String> request = new HttpEntity<>(query, buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        CaseSearchResult caseSearchResult;
        int retries = 1;
        do {
            log.info("Retry: " + retries);
            caseSearchResult = restTemplate.exchange(url, HttpMethod.POST, request, CaseSearchResult.class).getBody();
            try {
                if (caseSearchResult != null) {
                    log.info("Checking size found: " + caseSearchResult.getTotal());
                }
                TimeUnit.SECONDS.sleep(5);
                retries++;
                if (retries == MAX_RETRIES) {
                    break;
                }
            } catch (InterruptedException e) {
                log.error("Error sleeping the thread");
                Thread.currentThread().interrupt();
            }
        } while (caseSearchResult == null || caseSearchResult.getTotal() != size);

        if (caseSearchResult != null) {
            generateCasesNotFound(caseIds, caseSearchResult);
        }
        return caseSearchResult != null
                ? new ArrayList<>(caseSearchResult.getCases())
                : new ArrayList<>();
    }

    public static void generateCasesNotFound(List<String> caseIds, CaseSearchResult caseSearchResult) {
        List<String> casesFound = caseSearchResult.getCases()
                .stream()
                .map(caseSearched -> caseSearched.getCaseData().getEthosCaseReference())
                .collect(Collectors.toList());
        log.info("Cases not found: " + new ArrayList<>(CollectionUtils.subtract(caseIds, casesFound)).toString());
    }

    public List<SubmitEvent> retrieveCasesElasticSearchForCreation(String authToken, String caseTypeId, List<String> caseIds, String multipleSource) throws IOException {
        if (multipleSource.equals(MANUALLY_CREATED_POSITION)) {
            return retrieveCasesElasticSearch(authToken, caseTypeId, caseIds);
        } else {
            return retrieveCasesElasticSearchWithRetries(authToken, caseTypeId, caseIds);
        }
    }

    private List<SubmitEvent> retrieveCasesElasticSearchWithRetries(String authToken, String caseTypeId, List<String> caseIds) throws IOException {
        String query = ESHelper.getSearchQuery(caseIds);
        log.info("QUERY WITH RETRIES: " + query);
        return buildAndGetElasticSearchRequestWithRetries(authToken, caseTypeId, query, caseIds.size(), caseIds);
    }

    public List<SubmitEvent> retrieveCasesElasticSearch(String authToken, String caseTypeId, List<String> caseIds) throws IOException {
        String query = ESHelper.getSearchQuery(caseIds);
        log.info("QUERY: " + query);
        return buildAndGetElasticSearchRequest(authToken, caseTypeId, query);
    }

    public List<SchedulePayloadEvent> retrieveCasesElasticSearchSchedule(String authToken, String caseTypeId, List<String> caseIds) throws IOException {
        String query = ESHelper.getSearchQuerySchedule(caseIds);
        log.info("QUERY Schedule: " + query);
        return buildAndGetElasticSearchScheduleRequest(authToken, caseTypeId, query);
    }

    public List<LabelPayloadEvent> retrieveCasesElasticSearchLabels(String authToken, String caseTypeId, List<String> caseIds) throws IOException {
        String query = ESHelper.getSearchQueryLabels(caseIds);
        log.info("QUERY Labels: " + query);
        return buildAndGetElasticSearchLabelRequest(authToken, caseTypeId, query);
    }

    public List<SubmitBulkEvent> retrieveBulkCases(String authToken, String caseTypeId, String jurisdiction) throws IOException {
        List<SubmitBulkEvent> submitBulkEvents = new ArrayList<>();
        int totalNumberPages = getTotalPagesCount(authToken, caseTypeId, jurisdiction);
        for (int page = 1; page <= totalNumberPages; page++) {
            List<SubmitBulkEvent> submitBulkEventAux = restTemplate.exchange(getURI(authToken, caseTypeId, jurisdiction, String.valueOf(page)), HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(authToken)), new ParameterizedTypeReference<List<SubmitBulkEvent>>(){}).getBody();
            if (submitBulkEventAux != null) {
                submitBulkEvents.addAll(submitBulkEventAux);
            }
        }
        return submitBulkEvents;
    }

    public List<SubmitBulkEvent> retrieveBulkCasesElasticSearch(String authToken, String caseTypeId, String multipleReference) throws IOException {
        List<SubmitBulkEvent> submitBulkEvents = new ArrayList<>();
        log.info("QUERY: " + ESHelper.getBulkSearchQuery(multipleReference));
        HttpEntity<String> request =
                new HttpEntity<>(ESHelper.getBulkSearchQuery(multipleReference), buildHeaders(authToken));
        //log.info("REQUEST: " + request);
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        BulkCaseSearchResult bulkCaseSearchResult = restTemplate.exchange(url, HttpMethod.POST, request, BulkCaseSearchResult.class).getBody();
        if (bulkCaseSearchResult != null && bulkCaseSearchResult.getCases() != null) {
            submitBulkEvents.addAll(bulkCaseSearchResult.getCases());
        }
        return submitBulkEvents;
    }

    public List<SubmitMultipleEvent> retrieveMultipleCasesElasticSearchWithRetries(String authToken, String caseTypeId, String multipleReference) throws IOException {
        log.info("QUERY WITH RETRIES: " + ESHelper.getBulkSearchQuery(multipleReference));
        return buildAndGetElasticSearchRequestWithRetriesMultiples(authToken, caseTypeId, ESHelper.getBulkSearchQuery(multipleReference));
    }

    public List<SubmitMultipleEvent> retrieveMultipleCasesElasticSearch(String authToken, String caseTypeId, String multipleReference) throws IOException {
        List<SubmitMultipleEvent> submitMultipleEvents = new ArrayList<>();
        log.info("QUERY: " + ESHelper.getBulkSearchQuery(multipleReference));
        HttpEntity<String> request =
                new HttpEntity<>(ESHelper.getBulkSearchQuery(multipleReference), buildHeaders(authToken));
        String url = ccdClientConfig.buildRetrieveCasesUrlElasticSearch(caseTypeId);
        MultipleCaseSearchResult multipleCaseSearchResult = restTemplate.exchange(
                url, HttpMethod.POST, request, MultipleCaseSearchResult.class).getBody();
        if (multipleCaseSearchResult != null && multipleCaseSearchResult.getCases() != null) {
            submitMultipleEvents.addAll(multipleCaseSearchResult.getCases());
        }
        return submitMultipleEvents;
    }

    public CCDRequest startEventForCase(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startEventForCaseAPIRole(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForCaseUrlAPIRole(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startEventForCaseBulkSingle(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForCaseUrlBulkSingle(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startEventForCasePreAcceptBulkSingle(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForCaseUrlPreAcceptBulkSingle(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startBulkEventForCase(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForBulkCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public CCDRequest startBulkAmendEventForCase(String authToken, String caseTypeId, String jurisdiction, String cid) throws IOException {
        HttpEntity<String> request =
                new HttpEntity<>(buildHeaders(authToken));
        String uri = ccdClientConfig.buildStartEventForBulkAmendCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.GET, request, CCDRequest.class).getBody();
    }

    public SubmitEvent submitEventForCase(String authToken, CaseData caseData, String caseTypeId, String jurisdiction, CCDRequest req, String cid)
            throws IOException {
        HttpEntity<CaseDataContent> request =
                new HttpEntity<>(caseDataBuilder.buildCaseDataContent(caseData, req, UPDATE_EVENT_SUMMARY), buildHeaders(authToken));
        String uri = ccdClientConfig.buildSubmitEventForCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.POST, request, SubmitEvent.class).getBody();
    }

    public SubmitBulkEvent submitBulkEventForCase(String authToken, BulkData bulkData, String caseTypeId, String jurisdiction, CCDRequest req, String cid)
            throws IOException {
        HttpEntity<CaseDataContent> request =
                new HttpEntity<>(caseDataBuilder.buildBulkDataContent(bulkData, req, UPDATE_BULK_EVENT_SUMMARY), buildHeaders(authToken));
        String uri = ccdClientConfig.buildSubmitEventForCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.POST, request, SubmitBulkEvent.class).getBody();
    }

    public SubmitMultipleEvent submitMultipleEventForCase(String authToken, MultipleData multipleData, String caseTypeId, String jurisdiction, CCDRequest req, String cid)
            throws IOException {
        HttpEntity<CaseDataContent> request =
                new HttpEntity<>(caseDataBuilder.buildMultipleDataContent(multipleData, req, UPDATE_BULK_EVENT_SUMMARY), buildHeaders(authToken));
        String uri = ccdClientConfig.buildSubmitEventForCaseUrl(userService.getUserDetails(authToken).getUid(), jurisdiction,
                caseTypeId, cid);
        return restTemplate.exchange(uri, HttpMethod.POST, request, SubmitMultipleEvent.class).getBody();
    }

    HttpHeaders buildHeaders(String authToken) throws IOException {
        if (!authToken.matches("[a-zA-Z0-9._\\s\\S]+$")) {
            throw new IOException("authToken regex exception");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authToken);
        headers.add(SERVICE_AUTHORIZATION, authTokenGenerator.generate());
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
        return headers;
    }

}
