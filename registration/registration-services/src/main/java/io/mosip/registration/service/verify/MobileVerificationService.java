package io.mosip.registration.service.verify;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class MobileVerificationService {

    private static final Logger LOGGER =
            AppConfig.getLogger(MobileVerificationService.class);

    private static final String STATUS_SUCCESS = "SUCCESS";

    @Value("${mobile.verification.service.url}")
    private String verifyUrl;

    @Value("${auth.service.url}")
    private String authUrl;

    private final RestTemplate restTemplate;

    public MobileVerificationService() {
        this.restTemplate = createRestTemplate();
    }

    /**
     * Main verification method
     */
    public boolean verify(String id, String phone, boolean isCitizen) {

        if (id == null || phone == null || phone.length() < 4) {
            LOGGER.error("Invalid input for mobile verification");
            return false;
        }

        LOGGER.info("Verifying mobile -> ID: {}, Phone: ****{}, isCitizen: {}",
                id, phone.substring(phone.length() - 4), isCitizen);

        try {

            if (!authenticate()) {
                LOGGER.error("Authentication failed");
                return false;
            }

            return callVerifyApi(id, phone, isCitizen);

        } catch (Exception e) {
            LOGGER.error("Mobile verification error", e);
            return false;
        }
    }

    /**
     * Create RestTemplate with timeout
     */
    private RestTemplate createRestTemplate() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5 sec connect timeout
        factory.setReadTimeout(15000);     // 10 sec read timeout

        return new RestTemplate(factory);
    }

    /**
     * Authenticate with AuthManager
     */
    private boolean authenticate() {

        try {

            AuthRequest request = new AuthRequest();
            request.setId("mosip.identity.auth");
            request.setVersion("1.0");
            request.setRequesttime(LocalDateTime.now().toString());
            request.setRequest(
                    new AuthRequestDetails(
                            io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                                    RegistrationConstants.AUTH_CLIENT_ID),
                            io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                                    RegistrationConstants.AUTH_CLIENT_SECRET),
                            io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                                    RegistrationConstants.AUTH_APP_ID))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AuthRequest> entity = new HttpEntity<>(request, headers);

            LOGGER.debug("Calling Auth API");

            ResponseEntity<AuthResponse> response =
                    restTemplate.postForEntity(authUrl, entity, AuthResponse.class);

            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().getResponse() != null) {

                String status = response.getBody().getResponse().getStatus();

                LOGGER.info("Auth status: {}", status);

                return STATUS_SUCCESS.equalsIgnoreCase(status);
            }

            LOGGER.error("Invalid auth response");
            return false;

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LOGGER.error("Auth API HTTP error: {}", e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            LOGGER.error("Auth API timeout or connection error", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected Auth API error", e);
        }

        return false;
    }

    /**
     * Call Mobile Verify API
     */
    private boolean callVerifyApi(String id, String phone, boolean isCitizen) {

        try {

            String url = UriComponentsBuilder
                    .fromHttpUrl(verifyUrl)
                    .queryParam(RegistrationConstants.PARAM_ID, id)
                    .queryParam(RegistrationConstants.PHONE_NO, phone)
                    .queryParam("isCitizen", isCitizen)
                    .toUriString();

            LOGGER.info("Calling Mobile Verify API");
            LOGGER.debug("Verify params -> id={}, phone=****{}, isCitizen={}",
                    id, phone.substring(phone.length() - 4), isCitizen);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<VerifyResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            VerifyResponse.class
                    );

            LOGGER.info("Verify API HTTP status: {}", response.getStatusCodeValue());

            if (response.getBody() != null &&
                    STATUS_SUCCESS.equalsIgnoreCase(response.getBody().getStatus())) {

                LOGGER.info("Mobile verification SUCCESS for id={}", id);
                return true;
            }

            LOGGER.warn("Mobile verification FAILED for id={}, response={}",
                    id, response.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LOGGER.error("Verify API HTTP error: {}", e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            LOGGER.error("Verify API timeout or connection error", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while calling Verify API", e);
        }

        return false;
    }

    /**
     * ================= DTO CLASSES =================
     */

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class AuthRequest {
        private String id;
        private String version;
        private String requesttime;
        private AuthRequestDetails request;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class AuthRequestDetails {
        private String clientId;
        private String secretKey;
        private String appId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AuthResponse {
        private AuthResponseDetails response;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AuthResponseDetails {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class VerifyResponse {
        private String status;
        private String message;
    }
}