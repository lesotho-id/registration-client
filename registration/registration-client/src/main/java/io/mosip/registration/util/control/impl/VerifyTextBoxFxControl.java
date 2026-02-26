package io.mosip.registration.util.control.impl;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.controller.GenericController;
import io.mosip.registration.controller.reg.Validations;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.service.verify.MobileVerificationService;
import io.mosip.registration.util.control.FxControl;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.context.ApplicationContext;

public class VerifyTextBoxFxControl extends TextFieldFxControl {

    private static final Logger LOGGER =
            AppConfig.getLogger(VerifyTextBoxFxControl.class);

    private final MobileVerificationService mobileVerificationService;
    private final Validations validation;

    private static final double FIELD_PERCENT = 48.0;
    private static final double ACTION_PERCENT = 52.0;

    public VerifyTextBoxFxControl() {
        ApplicationContext ctx = ClientApplication.getApplicationContext();
        this.mobileVerificationService =
                ctx.getBean(MobileVerificationService.class);
        this.validation =
                ctx.getBean(Validations.class);

        LOGGER.info("VerifyTextBoxFxControl initialized");
    }

    @Override
    public FxControl build(UiFieldDTO uiFieldDTO) {

        LOGGER.info("Building VerifyTextBoxFxControl for fieldId={}", uiFieldDTO.getId());

        FxControl baseControl = super.build(uiFieldDTO);
        Node fieldNode = baseControl.getNode();
        TextField textField = (TextField) fieldNode.lookup(".text-field");

        boolean verifyEnabled = uiFieldDTO.isVerifyEnabled();
        LOGGER.debug("verifyEnabled={} for fieldId={}", verifyEnabled, uiFieldDTO.getId());

        CheckBox verifiedCheckBox = new CheckBox();
        verifiedCheckBox.setDisable(true);
        verifiedCheckBox.setSelected(false);
        verifiedCheckBox.getStyleClass().add("verified-checkbox");

        Button verifyButton = new Button(uiFieldDTO.getVerifyButtonLabel());
        verifyButton.setDisable(true);
        verifyButton.getStyleClass().add("verify-button");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(18, 18);
        spinner.setVisible(false);

        StackPane verifyButtonContainer = new StackPane(verifyButton, spinner);
        StackPane.setAlignment(spinner, Pos.CENTER);

        HBox rightBox = new HBox(15, verifyButtonContainer, verifiedCheckBox);
        rightBox.setAlignment(Pos.CENTER_LEFT);
        rightBox.setPadding(new Insets(0, 0, 0, 50));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(5);

        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setPercentWidth(FIELD_PERCENT);
        fieldCol.setHgrow(Priority.ALWAYS);

        ColumnConstraints actionCol = new ColumnConstraints();
        actionCol.setPercentWidth(ACTION_PERCENT);
        actionCol.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(fieldCol, actionCol);
        grid.add(fieldNode, 0, 0);
        grid.add(verifyEnabled ? rightBox : new Region(), 1, 0);

        if (verifyEnabled && textField != null) {
            textField.textProperty().addListener((obs, oldVal, newVal) -> {
                boolean hasText = newVal != null && !newVal.isBlank();
                verifyButton.setDisable(!hasText);

                if (oldVal != null && !oldVal.equals(newVal)) {
                    LOGGER.debug("Phone value changed, resetting verification flag");
                    verifiedCheckBox.setSelected(false);
                    setIsVerified(false);
                }
            });
        }

        verifyButton.setOnAction(event -> {

            LOGGER.info("Verify button clicked");

            String primaryFieldId =
                    io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                            RegistrationConstants.PRIMARY_VERIFIER);

            String secondaryFieldId =
                    io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                            RegistrationConstants.SECONDARY_VERIFIER);

            String phone = getFieldValue(primaryFieldId);
            String idNumber = getFieldValue(secondaryFieldId);

            LOGGER.debug("Verification inputs -> phone={}, nationalId={}", phone, idNumber);

            if (phone == null || phone.isBlank()) {
                validation.generateAlert(
                        RegistrationConstants.ERROR,
                        RegistrationUIConstants.INVALID_PHONE);
                return;
            }

            if (idNumber == null || idNumber.isBlank()) {
                validation.generateAlert(
                        RegistrationConstants.ERROR,
                        RegistrationUIConstants.INVALID_ID);
                return;
            }

            verifyButton.setDisable(true);
            spinner.setVisible(true);

            Task<Boolean> verificationTask = new Task<>() {
                @Override
                protected Boolean call() {
                    LOGGER.info("Calling mobile verification service");
                    return mobileVerificationService.verify(idNumber, phone);
                }
            };

            verificationTask.setOnSucceeded(e -> {
                spinner.setVisible(false);
                boolean verified = verificationTask.getValue();

                LOGGER.info("Mobile verification result={}", verified);

                if (verified) {
                    verifiedCheckBox.setSelected(true);
                    setIsVerified(true);
                    LOGGER.info("Mobile verification successful, isVerified=true set in demographics");
                } else {
                    verifiedCheckBox.setSelected(false);
                    verifyButton.setDisable(false);
                    setIsVerified(false);

                    validation.generateAlert(
                            RegistrationConstants.ERROR,
                            RegistrationUIConstants.PHONE_VERIFICATION_FAILED
                    );
                }
            });

            verificationTask.setOnFailed(e -> {
                spinner.setVisible(false);
                verifyButton.setDisable(false);
                setIsVerified(false);

                LOGGER.error("Verification failed due to exception",
                        verificationTask.getException());

                validation.generateAlert(
                        RegistrationConstants.ERROR,
                        RegistrationUIConstants.VERIFICATION_ERROR
                );
            });

            new Thread(verificationTask).start();
        });

        this.node = grid;
        return this;
    }

    private void setIsVerified(boolean value) {

        RegistrationDTO regDTO =
                ClientApplication.getApplicationContext()
                        .getBean(GenericController.class)
                        .getRegistrationDTOFromSession();

        if (regDTO != null && regDTO.getDemographics() != null) {

            LOGGER.debug(
                    "Setting demographic field isVerified as BOOLEAN value={}, class={}",
                    value,
                    Boolean.class.getName()
            );

            regDTO.getDemographics()
                    .put(io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap(
                            RegistrationConstants.VERIFIED_FLAG), value);

        } else {

            LOGGER.error(
                    "RegistrationDTO or demographics map is null while setting isVerified"
            );
        }
    }



    private String getFieldValue(String fieldId) {
        FxControl fxControl = GenericController.getFxControlMap().get(fieldId);

        if (fxControl == null) {
            LOGGER.debug("No FxControl found for fieldId={}", fieldId);
            return null;
        }

        if (!fxControl.isValid()) {

            LOGGER.debug("Field {} is invalid as per schema validation", fieldId);
            return null;
        }

        Node node = fxControl.getNode();

        if (node != null) {

            TextField tf = (TextField) node.lookup(".text-field");

            if (tf != null) {

                String value = tf.getText();

                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }

                return null;
            }
        }

        if (fxControl.getData() != null) {

            String value = fxControl.getData().toString().trim();

            return value.isEmpty() ? null : value;
        }

        return null;
    }
}