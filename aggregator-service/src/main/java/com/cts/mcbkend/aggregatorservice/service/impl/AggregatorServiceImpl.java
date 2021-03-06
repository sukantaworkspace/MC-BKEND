package com.cts.mcbkend.aggregatorservice.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cts.mcbkend.aggregatorservice.feign.AuthCenter;
import com.cts.mcbkend.aggregatorservice.feign.DocumentService;
import com.cts.mcbkend.aggregatorservice.feign.LoanService;
import com.cts.mcbkend.aggregatorservice.feign.UserService;
import com.cts.mcbkend.aggregatorservice.model.DocumentDto;
import com.cts.mcbkend.aggregatorservice.model.LoanDocumentDto;
import com.cts.mcbkend.aggregatorservice.model.LoanDto;
import com.cts.mcbkend.aggregatorservice.model.UserDto;
import com.cts.mcbkend.aggregatorservice.model.UserLoanDto;
import com.cts.mcbkend.aggregatorservice.rest.event.ResponseEvent;
import com.cts.mcbkend.aggregatorservice.rest.exception.AggregatorRestException;
import com.cts.mcbkend.aggregatorservice.service.AggregatorService;
import com.cts.mcbkend.aggregatorservice.util.Constants;

@Service("AggregatorService")
public class AggregatorServiceImpl implements AggregatorService {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AggregatorServiceImpl.class);

	@Autowired
	private UserService userService;

	@Autowired
	private LoanService loanService;

	@Autowired
	private DocumentService documentService;

	@Autowired
	private AuthCenter authCenter;

	@Value(value = "${customer.username}")
	private String customerUsername;

	@Value(value = "${customer.password}")
	private String customerPassword;

	@Value(value = "${underwriter.username}")
	private String underwriterUsername;

	@Value(value = "${underwriter.password}")
	private String underwriterPassword;

	@Override
	public List<UserLoanDto> getAllUserLoanList(String authorizationHeader) throws Exception {

		List<UserLoanDto> userLoanDtoList = new ArrayList<UserLoanDto>();
		List<LoanDocumentDto> loanDocumentList = null;
		UserLoanDto userLoanDto = null;
		List<UserDto> userDtoList = null;
		List<LoanDto> loanDtoList = null;
		List<DocumentDto> documentDtoList = null;
		ResponseEvent<List<UserDto>> listUserDtoResponse = null;
		ResponseEvent<List<LoanDto>> listLoanDtoResponse = null;
		ResponseEvent<List<DocumentDto>> listDocumentDtoResponse = null;
		Set<Long> userIds = new HashSet<Long>();

		listUserDtoResponse = userService.getUserList(authorizationHeader);
		checkForFallBack(listUserDtoResponse);
		userDtoList = listUserDtoResponse.getPayload();
		if (userDtoList != null) {
			userDtoList.stream().forEach((userDto) -> {
				if (userDto.getRole() != null && userDto.getRole().equalsIgnoreCase(Constants.USER_ROLE_CUSTOMER)) {
					userIds.add(userDto.getId());
				}
			});

			listLoanDtoResponse = loanService.getMultipleLoans(authorizationHeader, new ArrayList<Long>(userIds));
			checkForFallBack(listLoanDtoResponse);
			loanDtoList = listLoanDtoResponse.getPayload();
			Map<Long, List<LoanDto>> userLoanMap = loanDtoList.stream()
					.collect(Collectors.groupingBy(w -> w.getUserId()));

			listDocumentDtoResponse = documentService.getMultipleDocuments(authorizationHeader,
					new ArrayList<Long>(userIds));
			checkForFallBack(listDocumentDtoResponse);
			documentDtoList = listDocumentDtoResponse.getPayload();
			Map<String, List<DocumentDto>> loanDocumentMap = documentDtoList.stream()
					.collect(Collectors.groupingBy(w -> w.getLoanNum()));

			for (UserDto eachUserDto : userDtoList) {
				if (eachUserDto.getRole() != null
						&& eachUserDto.getRole().equalsIgnoreCase(Constants.USER_ROLE_CUSTOMER)) {
					userLoanDto = new UserLoanDto();
					userLoanDto.setUserId(eachUserDto.getId());
					userLoanDto.setAddress(eachUserDto.getAddress());
					userLoanDto.setContacNo(eachUserDto.getContacNo());
					userLoanDto.setCountry(eachUserDto.getCountry());
					userLoanDto.setDob(eachUserDto.getDob());
					userLoanDto.setEmail(eachUserDto.getEmail());
					userLoanDto.setfName(eachUserDto.getfName());
					userLoanDto.setlName(eachUserDto.getlName());
					userLoanDto.setPan(eachUserDto.getPan());
					userLoanDto.setRole(eachUserDto.getRole());
					userLoanDto.setSsn(eachUserDto.getSsn());
					userLoanDto.setState(eachUserDto.getState());
					userLoanDto.setUserName(eachUserDto.getUserName());
					List<LoanDto> loanDtoListObj = userLoanMap.get(eachUserDto.getId());
					loanDocumentList = new ArrayList<LoanDocumentDto>();
					if (loanDtoListObj != null) {
						for (LoanDto eachLoanDtoObj : loanDtoListObj) {
							LoanDocumentDto loanDocumentDto = new LoanDocumentDto();
							loanDocumentDto.setDocumentList(loanDocumentMap.get(eachLoanDtoObj.getLoanNum()));
							loanDocumentDto.setLoanDesc(eachLoanDtoObj.getLoanDesc());
							loanDocumentDto.setLoanId(eachLoanDtoObj.getId());
							loanDocumentDto.setLoanNum(eachLoanDtoObj.getLoanNum());
							loanDocumentDto.setLoanStatus(eachLoanDtoObj.getLoanNum());
							loanDocumentDto.setLoanType(eachLoanDtoObj.getLoanType());
							loanDocumentDto.setLockId(eachLoanDtoObj.getLockId());
							loanDocumentDto.setVersion(eachLoanDtoObj.getVersion() + 1);
							loanDocumentList.add(loanDocumentDto);
						}
					}
					userLoanDto.setLoanDocumentList(loanDocumentList);
					userLoanDtoList.add(userLoanDto);

				}
			}
		}
		return userLoanDtoList;
	}

	@Override
	public UserLoanDto createUserWithLoan(String authorizationHeader, UserLoanDto userLoanDto) throws Exception {
		ResponseEvent<UserDto> userDtoResponse = null;
		ResponseEvent<LoanDto> loanDtoResponse = null;
		UserLoanDto userLoanDtoResponse = null;
		ArrayList<LoanDocumentDto> loanDocumentList = null;

		UserDto userDto = null;
		LoanDto loanDto = null;
		// User Creation
		userDto = new UserDto();
		userDto.setAddress(userLoanDto.getAddress());
		userDto.setContacNo(userLoanDto.getContacNo());
		userDto.setCountry(userLoanDto.getCountry());
		userDto.setDob(userLoanDto.getDob());
		userDto.setEmail(userLoanDto.getEmail());
		userDto.setfName(userLoanDto.getfName());
		userDto.setlName(userLoanDto.getlName());
		userDto.setPan(userLoanDto.getPan());
		userDto.setRole(userLoanDto.getRole());
		userDto.setSsn(userLoanDto.getSsn());
		userDto.setState(userLoanDto.getState());
		userDto.setUserName(userLoanDto.getUserName());
		userDto.setPassword(userLoanDto.getPassword());

		userDtoResponse = userService.registerUser(authorizationHeader, userDto);
		if (userDtoResponse != null) {
			userDto = userDtoResponse.getPayload();
			if (userDto != null && userDto.getId() != null && userDto.getId() > 0) {
				LOGGER.info("user registered {}", userDto);
				// Set users
				try {
					userLoanDtoResponse = new UserLoanDto();
					userLoanDtoResponse.setUserId(userDto.getId());
					userLoanDtoResponse.setAddress(userDto.getAddress());
					userLoanDtoResponse.setContacNo(userDto.getContacNo());
					userLoanDtoResponse.setCountry(userDto.getCountry());
					userLoanDtoResponse.setDob(userDto.getDob());
					userLoanDtoResponse.setEmail(userDto.getEmail());
					userLoanDtoResponse.setfName(userDto.getfName());
					userLoanDtoResponse.setlName(userDto.getlName());
					userLoanDtoResponse.setPan(userDto.getPan());
					userLoanDtoResponse.setRole(userDto.getRole());
					userLoanDtoResponse.setSsn(userDto.getSsn());
					userLoanDtoResponse.setState(userDto.getState());
					userLoanDtoResponse.setUserName(userDto.getUserName());
					if (userDto.getRole() != null && userDto.getRole().equalsIgnoreCase(Constants.USER_ROLE_CUSTOMER)) {
						// Loan Creation
						loanDto = new LoanDto();
						loanDto.setLoanDesc(userLoanDto.getLoanDocumentList().get(0).getLoanDesc());
						loanDto.setLoanNum(userLoanDto.getLoanDocumentList().get(0).getLoanNum());
						loanDto.setLoanStatus(userLoanDto.getLoanDocumentList().get(0).getLoanNum());
						loanDto.setLoanType(userLoanDto.getLoanDocumentList().get(0).getLoanType());
						loanDto.setUserId(userDto.getId());

						loanDtoResponse = loanService.createNewLoan(authorizationHeader, loanDto);
						if (loanDtoResponse != null) {
							checkForFallBack(loanDtoResponse);
							loanDto = loanDtoResponse.getPayload();
							if (loanDto != null && loanDto.getId() != null && loanDto.getId() > 0) {
								LOGGER.info("loan created {}", loanDto);
								// Set loans
								loanDocumentList = new ArrayList<LoanDocumentDto>();
								LoanDocumentDto loanDocumentDto = new LoanDocumentDto();
								loanDocumentDto.setLoanDesc(loanDto.getLoanDesc());
								loanDocumentDto.setLoanId(loanDto.getId());
								loanDocumentDto.setLoanNum(loanDto.getLoanNum());
								loanDocumentDto.setLoanStatus(loanDto.getLoanNum());
								loanDocumentDto.setLoanType(loanDto.getLoanType());
								loanDocumentDto.setLockId(loanDto.getLockId());
								loanDocumentDto.setVersion(loanDto.getVersion() + 1);
								loanDocumentList.add(loanDocumentDto);
								userLoanDtoResponse.setLoanDocumentList(loanDocumentList);
							} else {
								deleteUserById(userDto, authorizationHeader);
							}
						} else {
							deleteUserById(userDto, authorizationHeader);
						}
					}
				} catch (Exception e) {
					deleteUserById(userDto, authorizationHeader);
				}
			} else {
				checkForFallBack(userDtoResponse);
				createCustomizedException("User creation failed", HttpStatus.FAILED_DEPENDENCY);
			}
		} else {
			createCustomizedException("User creation failed", HttpStatus.FAILED_DEPENDENCY);
		}

		return userLoanDtoResponse;

	}

	@Override
	public UserLoanDto loginUser(Map<String, String> body) throws Exception {
		ResponseEntity<Object> responseEntity = null;
		UserDto userDtoRequest = null;
		UserLoanDto userLoanDto = null;
		ResponseEvent<UserDto> userDtoResponse = null;
		userDtoRequest = new UserDto();
		userDtoRequest.setUserName(body.get("username"));
		userDtoRequest.setPassword(body.get("password"));
		userDtoResponse = userService.loginUser(userDtoRequest);
		if (userDtoResponse != null) {
			checkForFallBack(userDtoResponse);
			userDtoRequest = null;
			userDtoRequest = userDtoResponse.getPayload();
			if (userDtoRequest != null && userDtoRequest.getId() != null && userDtoRequest.getId() > 0) {
				userLoanDto = new UserLoanDto();
				userLoanDto.setUserId(userDtoRequest.getId());
				userLoanDto.setAddress(userDtoRequest.getAddress());
				userLoanDto.setContacNo(userDtoRequest.getContacNo());
				userLoanDto.setCountry(userDtoRequest.getCountry());
				userLoanDto.setDob(userDtoRequest.getDob());
				userLoanDto.setEmail(userDtoRequest.getEmail());
				userLoanDto.setfName(userDtoRequest.getfName());
				userLoanDto.setlName(userDtoRequest.getlName());
				userLoanDto.setPan(userDtoRequest.getPan());
				userLoanDto.setRole(userDtoRequest.getRole());
				userLoanDto.setSsn(userDtoRequest.getSsn());
				userLoanDto.setState(userDtoRequest.getState());
				userLoanDto.setUserName(userDtoRequest.getUserName());
				if (userDtoRequest.getRole().equalsIgnoreCase(Constants.USER_ROLE_CUSTOMER)) {
					body.put("username", customerUsername);
					body.put("password", customerPassword);
				} else if (userDtoRequest.getRole().equalsIgnoreCase(Constants.USER_ROLE_DU)) {
					body.put("username", underwriterUsername);
					body.put("password", underwriterPassword);
				}
				responseEntity = authCenter.getUserAuthorization(body);
				if (responseEntity != null && !responseEntity.getHeaders().isEmpty()
						&& responseEntity.getHeaders().get("Authorization") != null
						&& responseEntity.getHeaders().get("Authorization").toString().length() > 0) {
					LOGGER.info("O-Auth token------------ {}", responseEntity.getHeaders().get("Authorization"));
					userLoanDto.setAuthToken(responseEntity.getHeaders().get("Authorization").toString());
				} else {
					createCustomizedException("Token generation error!! try agin!", HttpStatus.FAILED_DEPENDENCY);
				}
			} else {
				createCustomizedException("Login failed, try agin!", HttpStatus.FAILED_DEPENDENCY);
			}
		} else {
			createCustomizedException("Login failed, try agin!", HttpStatus.FAILED_DEPENDENCY);
		}
		return userLoanDto;
	}

	private void deleteUserById(UserDto userDto, String authorizationHeader) throws Exception {
		ResponseEvent<String> deletedUserResponse = userService.deleteUser(authorizationHeader, userDto.getId(),
				userDto);
		String response = deletedUserResponse.getPayload();
		if (response != null && response.contains("Constants.SUCCESS")) {
			LOGGER.info("{} --User deleted successfully from aggregator service, message {}", authorizationHeader,
					response);
		} else {
			LOGGER.error("{} --User deletion failed with flying colours!!!", authorizationHeader);
		}
		createCustomizedException("Loan creation failed", HttpStatus.FAILED_DEPENDENCY);
	}

	private void checkForFallBack(ResponseEvent<? extends Object> responseEvent) throws Exception {
		if (responseEvent.getError() != null) {
			AggregatorRestException aggregatorRestException = new AggregatorRestException();
			aggregatorRestException.setErrorCode(HttpStatus.BAD_REQUEST);
			aggregatorRestException.setErrorMessage(responseEvent.getError().getErrorMessage());
			throw aggregatorRestException;
		}
	}

	private void createCustomizedException(String errorMessage, HttpStatus statusCode) throws Exception {
		AggregatorRestException aggregatorRestException = new AggregatorRestException();
		aggregatorRestException.setErrorCode(statusCode);
		aggregatorRestException.setErrorMessage(errorMessage);
		throw aggregatorRestException;
	}

}