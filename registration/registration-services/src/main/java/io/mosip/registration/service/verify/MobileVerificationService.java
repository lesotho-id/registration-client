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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class MobileVerificationService {

    private static final Logger LOGGER =
            AppConfig.getLogger(MobileVerificationService.class);

    @Value("${mobile.verification.service.url}")
    private String verifyUrl;

    @Value("${auth.service.url}")
    private String authUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean verify(String id, String phone) {

        LOGGER.info("Verifying mobile -> ID: {}, Phone: {}",
                id, phone);

        try {

            if (!authenticate()) {
                LOGGER.error("Authentication failed");
                return false;
            }

            return callVerifyApi(id, phone);

        } catch (Exception e) {
            LOGGER.error("Mobile verification error", e);
            return false;
        }
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
                    new AuthRequestDetails(io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                            RegistrationConstants.AUTH_CLIENT_ID),
                            io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                                    RegistrationConstants.AUTH_CLIENT_SECRET),
                            io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                                    RegistrationConstants.AUTH_APP_ID))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AuthRequest> entity =
                    new HttpEntity<>(request, headers);

            LOGGER.debug("Calling Auth API: {}", authUrl);

            ResponseEntity<AuthResponse> response =
                    restTemplate.postForEntity(
                            authUrl,
                            entity,
                            AuthResponse.class
                    );

            if (response.getStatusCode() == HttpStatus.OK &&
                    response.getBody() != null &&
                    response.getBody().getResponse() != null) {

                String status =
                        response.getBody()
                                .getResponse()
                                .getStatus();

                LOGGER.info("Auth status: {}", status);

                return "Success".equalsIgnoreCase(status);
            }

            LOGGER.error("Invalid auth response");
            return false;

        } catch (Exception e) {
            LOGGER.error("Auth API error", e);
            return false;
        }
    }

    /**
     * Call Mobile Verify API
     */
    private boolean callVerifyApi(String id, String phone) {

        try {

            String url = UriComponentsBuilder
                    .fromHttpUrl(verifyUrl)
                    .queryParam(RegistrationConstants.PARAM_ID, id)
                    .queryParam(RegistrationConstants.PHONE_NO, phone)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(
                    MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            LOGGER.info("Calling Verify API URL: {}", url);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            LOGGER.info("Verify response code: {}",
                    response.getStatusCodeValue());

            return response.getStatusCode() == HttpStatus.OK;

        } catch (Exception e) {
            LOGGER.error("Verify API error", e);
            return false;
        }
    }


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
}