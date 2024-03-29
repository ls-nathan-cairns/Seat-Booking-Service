package nz.ac.auckland.concert.client.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import nz.ac.auckland.concert.common.Config;
import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.common.types.SeatRow;
import nz.ac.auckland.concert.service.services.ConcertApplication;
import nz.ac.auckland.concert.utility.TheatreLayout;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for a ConcertService implementation.
 * 
 * Prior to running each test, an embedded servlet container is started up that
 * hosts a named Web service. Immediately after each test, the servlet 
 * container is stopped. Testing is handled in this way so that the Web service
 * can reinitialise its database. Since an embedded H2 database can only have 
 * one connection at a time, it's not possible for the @After method in this 
 * Test class to connect to the H2 database to delete data - hence restarting
 * the Web service before each test allows the Web service to clear from the
 * database the effects of the tests. 
 * 
 * This Test class has 3 configurable properties:
 * 
 * - SERVER_PORT                        the port number on which the servlet 
 *                                      container listens for incoming HTTP 
 *                                      requests.
 * - WEB_SERVICE_CLASS_NAME             the name of the javax.ws.rs.core.Application
 *                                      subclass for the Web service 
 *                                      application.
 * - RESERVATION_EXPIRY_TIME_IN_SECONDS the expiry time for reservations.
 *
 */
public class ConcertServiceTest {
	
	private static Logger _logger = LoggerFactory
			.getLogger(ConcertServiceTest.class);
	
	private static final int SERVER_PORT = 10000;
	private static final String WEB_SERVICE_CLASS_NAME = ConcertApplication.class.getName();
	private static final int RESERVATION_EXPIRY_TIME_IN_SECONDS = 5;
	
	private static Client _client;
	private static Server _server;
	
	private ConcertService _service;

	@BeforeClass
	public static void createClientAndServer() throws Exception {
		// Use ClientBuilder to create a new client that can be used to create
		// connections to the Web service.
		_client = ClientBuilder.newClient();
		
		// Start the embedded servlet container and host the Web service.
		ServletHolder servletHolder = new ServletHolder(new HttpServletDispatcher());
		servletHolder.setInitParameter("javax.ws.rs.Application", WEB_SERVICE_CLASS_NAME);
		ServletContextHandler servletCtxHandler = new ServletContextHandler();
		servletCtxHandler.setContextPath("/services");
		servletCtxHandler.addServlet(servletHolder, "/");
		_server = new Server(SERVER_PORT);
		_server.setHandler(servletCtxHandler);
	}
	
	@AfterClass
	public static void shutDown() {
		_client.close();
	}
	
	@Before 
	public void startServer() throws Exception {
		_server.start();
		_service = new DefaultService();
	}

	@After
	public void stopServer() throws Exception {
		_server.stop();
	}
	
	@Test
	public void testRetrieveConcerts() {
		final int numberOfConcerts = 25;
		
		Set<ConcertDTO> concerts = _service.getConcerts();
		assertEquals(numberOfConcerts, concerts.size());
	}
	
	@Test
	public void testRetrievePerformers() {
		final int numberOfPerformers = 20;
		
		Set<PerformerDTO> performers = _service.getPerformers();
		assertEquals(numberOfPerformers, performers.size());
	}
	
	@Test
	public void testCreateUser() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
		} catch(ServiceException e) {
			_logger.debug(e.getMessage());
			fail();
		}
	}
	
	@Test
	public void testCreateUserWithMissingField() {
		try {
			UserDTO userDTO = new UserDTO(null, "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.CREATE_USER_WITH_MISSING_FIELDS, e.getMessage());
		}
	}
	
	@Test 
	public void testCreateUserWithDuplicateUsername() {
		boolean createdFirstUser = false;
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			createdFirstUser = true;
			
			userDTO = new UserDTO("Bulldog", "123", "Thatcher", "Margaret");
			_service.createUser(userDTO);
			fail();
		} catch(ServiceException e) {
			if(!createdFirstUser) {
				fail();
			}
			assertEquals(Messages.CREATE_USER_WITH_NON_UNIQUE_NAME, e.getMessage());
		}
	}
	
	@Test
	public void testAuthenticateUser() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			UserDTO credentials = new UserDTO("Bulldog", "123");
			UserDTO filledDTO = _service.authenticateUser(credentials);
			
			assertEquals(userDTO, filledDTO);
		} catch(ServiceException e) {
			fail();
		}
	}
	
	@Test
	public void testAuthenticateWithNonExistentUser() {
		try {
			UserDTO credentials = new UserDTO("Bulldog", "123");
			_service.authenticateUser(credentials);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.AUTHENTICATE_NON_EXISTENT_USER, e.getMessage());
		}
	}
	
	@Test
	public void testAuthenticateUserWithIncorrectPassword() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			UserDTO credentials = new UserDTO("Bulldog", "987");
			_service.authenticateUser(credentials);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.AUTHENTICATE_USER_WITH_ILLEGAL_PASSWORD, e.getMessage());
		}
	}
	
	@Test
	public void testAuthenticateUserWithMissingPassword() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			UserDTO credentials = new UserDTO("Bulldog", null);
			_service.authenticateUser(credentials);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.AUTHENTICATE_USER_WITH_MISSING_FIELDS, e.getMessage());
		}
	}
	
	@Test
	public void testMakeReservation() {
		try {
			final int numberOfSeatsToBook = 2;
			
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(numberOfSeatsToBook, PriceBand.PriceBandC, 1L, dateTime);
			
			ReservationDTO reservation = _service.reserveSeats(request);
			
			ReservationRequestDTO requestFromResponse = reservation.getReservationRequest();
			assertEquals(request, requestFromResponse);
			
			Set<SeatDTO> reservedSeats = reservation.getSeats();
			assertEquals(numberOfSeatsToBook, reservedSeats.size());

			// Check that the seats reserved are of the required type.
			for(SeatDTO seat : reservedSeats) {
				assertTrue(TheatreLayout.getRowsForPriceBand(PriceBand.PriceBandC).contains(seat.getRow()));
			}
			
		} catch(ServiceException e) {
		    _logger.debug(e.getMessage());
		    e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testMakeReservationWithBadRequest() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			LocalDateTime dateTime = LocalDateTime.of(2018, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(2, PriceBand.PriceBandC, 1L, dateTime);
			
			ReservationDTO reservation = _service.reserveSeats(request);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.CONCERT_NOT_SCHEDULED_ON_RESERVATION_DATE, e.getMessage());
		}
	}
	
	@Test
	public void testMakeReservationWhereSeatsAreNotAvailable() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			Set<SeatRow> rowsOfStandardSeats = TheatreLayout.getRowsForPriceBand(PriceBand.PriceBandB);
			int totalNumberOfStandardSeats = 0;
			for(SeatRow row : rowsOfStandardSeats) {
				totalNumberOfStandardSeats += TheatreLayout.getNumberOfSeatsForRow(row);
			}
			
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(totalNumberOfStandardSeats, PriceBand.PriceBandB, 1L, dateTime);
			
			ReservationDTO reservation = _service.reserveSeats(request);
			
			ReservationRequestDTO requestFromResponse = reservation.getReservationRequest();
			assertEquals(request, requestFromResponse);
			
			Set<SeatDTO> reservedSeats = reservation.getSeats();
			assertEquals(totalNumberOfStandardSeats, reservedSeats.size());

			// Check that the seats reserved are of the required type.
			for(SeatDTO seat : reservedSeats) {
				assertTrue(TheatreLayout.getRowsForPriceBand(PriceBand.PriceBandB).contains(seat.getRow()));
			}
			
			// Attempt to reserve another seat.
			request = new ReservationRequestDTO(1, PriceBand.PriceBandB, 1L, dateTime);
			reservation = _service.reserveSeats(request);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.INSUFFICIENT_SEATS_AVAILABLE_FOR_RESERVATION, e.getMessage());
		}	
	}
	
	@Test
	public void testMakeReservationWithUnauthenticatedUser() {
		try {
			final int numberOfSeatsToBook = 10;
			
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(numberOfSeatsToBook, PriceBand.PriceBandA, 1L, dateTime);
			
			ReservationDTO reservation = _service.reserveSeats(request);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.UNAUTHENTICATED_REQUEST, e.getMessage());
		}
	}
	
	@Test
	public void testConfirmReservation() {
		try {
			final int numberOfSeatsToBook = 5;
			
			// Create a User and register a credit card.
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			CreditCardDTO creditCard = new CreditCardDTO(CreditCardDTO.Type.Visa, "Winston Churchill", "4929-1500-0055-9544", LocalDate.of(2019, 7, 31));
			_service.registerCreditCard(creditCard);
			
			// Make a reservation.
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(numberOfSeatsToBook, PriceBand.PriceBandC, 1L, dateTime);
			ReservationDTO reservation = _service.reserveSeats(request);
			
			// Confirm the reservation.
			_service.confirmReservation(reservation);
			
			// Make a request to check that this user has a booking.
			Set<BookingDTO> bookings = _service.getBookings();
			assertEquals(1, bookings.size());
			BookingDTO bookingDTO = bookings.iterator().next();
			assertEquals(new Long(1), bookingDTO.getConcertId());
			assertEquals(dateTime, bookingDTO.getDateTime());

			assertEquals(reservation.getSeats(), bookingDTO.getSeats());
			assertEquals(PriceBand.PriceBandC, bookingDTO.getPriceBand());
		} catch(ServiceException e) {
			fail();
		}
	}	
	
	@Test
	public void testConfirmBookingWithExpiredReservation() {
		try {
			final int numberOfSeatsToBook = 12;
			
			// Create a User and register a credit card.
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			CreditCardDTO creditCard = new CreditCardDTO(CreditCardDTO.Type.Visa, "Winston Churchill", "4929-1500-0055-9544", LocalDate.of(2019, 7, 31));
			_service.registerCreditCard(creditCard);
			
			// Make a reservation.
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(numberOfSeatsToBook, PriceBand.PriceBandA, 1L, dateTime);
			ReservationDTO reservation = _service.reserveSeats(request);
			
			// Wait for the reservation to expire. 
			Thread.sleep(RESERVATION_EXPIRY_TIME_IN_SECONDS * 1000);
			
			// Attempt to confirm the reservation.
			_service.confirmReservation(reservation);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.EXPIRED_RESERVATION, e.getMessage());
		} catch(InterruptedException e) {
			e.printStackTrace();
		} finally {
			// Make a request to check that this user doesn't have a booking.
			Set<BookingDTO> bookings = _service.getBookings();
			assertTrue(bookings.isEmpty());
		}
	}
	
	@Test
	public void testConfirmReservationWithoutRegisteredCreditCard() {
		try {
			final int numberOfSeatsToBook = 6;
			
			// Create a User.
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			// Make a reservation.
			LocalDateTime dateTime = LocalDateTime.of(2017, 2, 24, 17, 00);
			ReservationRequestDTO request = new ReservationRequestDTO(numberOfSeatsToBook, PriceBand.PriceBandB, 1L, dateTime);
			ReservationDTO reservation = _service.reserveSeats(request);
			
			// Attempt to confirm the reservation.
			_service.confirmReservation(reservation);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.CREDIT_CARD_NOT_REGISTERED, e.getMessage());
		} finally {
			// Make a request to check that this user doesn't have a booking.
			Set<BookingDTO> bookings = _service.getBookings();
			assertTrue(bookings.isEmpty());
		}
	}

	@Test
	public void testRegisterCreditCard() {
		try {
			UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
			_service.createUser(userDTO);
			
			CreditCardDTO creditCard = new CreditCardDTO(CreditCardDTO.Type.Visa, "Winston Churchill", "4929-1500-0055-9544", LocalDate.of(2019, 7, 31));
			_service.registerCreditCard(creditCard);
		} catch(ServiceException e) {
			fail();
		} 
	}
	
	@Test
	public void testRegisterCreditCardWithUnauthenticatedUser() {
		try {
			CreditCardDTO creditCard = new CreditCardDTO(CreditCardDTO.Type.Visa, "Winston Churchill", "4929-1500-0055-9544", LocalDate.of(2019, 7, 31));
			_service.registerCreditCard(creditCard);
			fail();
		} catch(ServiceException e) {
			assertEquals(Messages.UNAUTHENTICATED_REQUEST, e.getMessage());
		} 
	}

	@Test
    public void testDownloadImage() {
        Set<PerformerDTO> performers = _service.getPerformers();

        for (PerformerDTO performerDTO : performers) {
            try {
            	if (performerDTO.getName().equals("Twenty One Pilots")) {
					try {
						_service.getImageForPerformer(performerDTO);
					} catch (Exception e) {
					    // throws a rasta band exception
                        // Should be seen as a service communication error to the user
						assertEquals(e.getMessage(), Messages.SERVICE_COMMUNICATION_ERROR);
					}
				} else {
					_service.getImageForPerformer(performerDTO);
				}
            } catch (Exception e) {
                if (e instanceof ServiceException && performerDTO == null || performerDTO.equals("")) {
                    assertEquals(e.getMessage(), Messages.NO_IMAGE_FOR_PERFORMER);
                } else {
                    fail();
                }
            }
        }
    }

    @Test
	public void testCreateNewsItem() {
	    try {
            NewsItemDTO newsItemDTO = new NewsItemDTO("Bon Jovi Annouce New Concert!",
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris semper hendrerit sagittis. Vivamus " +
                            "vestibulum id ex eu gravida. Integer euismod.",
                    LocalDateTime.of(2017, 2, 24, 17, 00));
            if (_service instanceof NewsItemService) {
                ((NewsItemService) _service).createNewsItem(newsItemDTO);
            } else {
                _logger.debug("This service does not have the capability to make news items!");
                fail();
            }
        } catch (Exception e) {
	        _logger.debug(e.getMessage());
	        fail();
        }
	}

	@Test
    public void testSubscribeNewsItem() {
	    // All thread sleep stuff is in here to make sure objects are in the database and such!
	    try {
            NewsItemDTO newsItemDTO = new NewsItemDTO("Bon Jovi Annouce New Concert!",
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris semper hendrerit sagittis. Vivamus " +
                            "vestibulum id ex eu gravida. Integer euismod.",
                    LocalDateTime.of(2017, 2, 24, 17, 00));
            if (_service instanceof NewsItemService) {
                NewsItemService newsItemService = (NewsItemService) _service;
                newsItemService.createNewsItem(newsItemDTO);

                UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
                UserDTO userDTO1 = new UserDTO("SillyFace", "12345678", "Zach", "Zach");
                _service.createUser(userDTO);
                Thread.sleep(5000);
                newsItemService.newsItemSub();

                Thread.sleep(5000);

                _service.createUser(userDTO1);
                Thread.sleep(5000);
                _service.authenticateUser(userDTO1);
                Thread.sleep(5000);
                newsItemService.newsItemSub();

                Thread.sleep(5000);

                String title = "Coil Coming To NZ!";
                String body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam in dignissim quam, " +
                        "vel lobortis velit. Fusce in lacus tincidunt, mollis enim vel, vulputate eros. " +
                        "Sed at mi dignissim, rhoncus augue non, vestibulum magna. In et auctor quam." +
                        " Curabitur in nulla.";
                LocalDateTime localDateTime = LocalDateTime.of(2018, 4, 10,
                        18, 00);

                NewsItemDTO newsItemDTO1 = new NewsItemDTO(title, body, localDateTime);

                String title2 = "Masters of Synth";
                String body2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam in dignissim quam, " +
                        "vel lobortis velit. Fusce in lacus tincidunt, mollis enim vel, vulputate eros. " +
                        "Sed at mi dignissim, rhoncus augue non, vestibulum magna. In et auctor quam." +
                        " Curabitur in nulla.";
                LocalDateTime localDateTime2 = LocalDateTime.of(2300, 8, 11,
                        19, 30);

                NewsItemDTO newsItemDTO2 = new NewsItemDTO(title2, body2, localDateTime2);

                newsItemService.createNewsItem(newsItemDTO1);
                Thread.sleep(5000);
                assertEquals(newsItemService.getCurrentNewsItem(), newsItemDTO1);

                newsItemService.createNewsItem(newsItemDTO2);
                _logger.debug(newsItemService.getCurrentNewsItem().getTitle());
                _logger.debug(newsItemDTO2.getTitle());
                Thread.sleep(5000);
                assertEquals(newsItemService.getCurrentNewsItem(), newsItemDTO2);
            } else {
                _logger.debug("This service does not have the capability to make news items!");
                fail();
            }
        } catch (Exception e) {
	        fail();
        }
    }

    @Test
    public void testDeleteSubsription() {
	    try {
	        if (_service instanceof DefaultService) {
                NewsItemService newsItemService = (NewsItemService) _service;

                UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");

                _service.createUser(userDTO);
                newsItemService.newsItemSub();

                Thread.sleep(5000);

                String title = "Coil Coming To NZ!";
                String body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam in dignissim quam, " +
                        "vel lobortis velit. Fusce in lacus tincidunt, mollis enim vel, vulputate eros. " +
                        "Sed at mi dignissim, rhoncus augue non, vestibulum magna. In et auctor quam." +
                        " Curabitur in nulla.";
                LocalDateTime localDateTime = LocalDateTime.of(2018, 4, 10,
                        18, 00);

                NewsItemDTO newsItemDTO1 = new NewsItemDTO(title, body, localDateTime);

                newsItemService.createNewsItem(newsItemDTO1);

                newsItemService.cancelNewsItemSub();

                String title2 = "Masters of Synth";
                String body2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam in dignissim quam, " +
                        "vel lobortis velit. Fusce in lacus tincidunt, mollis enim vel, vulputate eros. " +
                        "Sed at mi dignissim, rhoncus augue non, vestibulum magna. In et auctor quam." +
                        " Curabitur in nulla.";
                LocalDateTime localDateTime2 = LocalDateTime.of(2300, 8, 11,
                        19, 30);

                assertEquals(newsItemService.getCurrentNewsItem(), newsItemDTO1);
            } else {
	            fail();
            }
        } catch (Exception e) {
	        e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testConcertCacheRetrieve() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should be retrieved from cache
        try {
            _service.getConcerts();
            _service.getConcerts();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }

    @Test
    public void testConcertCacheRevalidate() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should revalidate cache
        try {
            _service.getConcerts();
            Thread.sleep(Config.CACHE_EXPIRY_SECONDS * 1000); // expire cache
            _service.getConcerts();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }

    @Test
    public void testPerformerCacheRetrieve() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should be retrieved from cache
        try {
            _service.getPerformers();
            _service.getPerformers();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }

    @Test
    public void testPerformerCacheRevalidate() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should revalidate cache
        try {
            _service.getPerformers();
            Thread.sleep(Config.CACHE_EXPIRY_SECONDS * 1000); // expire cache
            _service.getPerformers();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }

    @Test
    public void testBookingCacheRetrieve() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should be retrieved from cache
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);
            _service.getBookings();
            _service.getBookings();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }

    @Test
    public void testBookingCacheRevalidate() {
	    // This test is via visual inspection check log statements are correct.
        // Second request should revalidate cache
        try {
            UserDTO userDTO = new UserDTO("Bulldog", "123", "Churchill", "Winston");
            _service.createUser(userDTO);
            _service.getBookings();
            Thread.sleep(Config.CACHE_EXPIRY_SECONDS * 1000); // expire cache
            _service.getBookings();
        } catch (Exception e) {
            _logger.debug(e.getMessage());
            fail();
        }
    }
}
