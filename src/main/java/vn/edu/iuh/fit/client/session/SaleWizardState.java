package vn.edu.iuh.fit.client.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import vn.edu.iuh.fit.common.constant.DocumentType;
import vn.edu.iuh.fit.common.constant.SeatType;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.SaleChildUnder6DTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;

public class SaleWizardState {
    private final String clientSessionId = UUID.randomUUID().toString();

    private StationDTO departureStation;
    private StationDTO destinationStation;
    private LocalDate departureDate;
    private LocalDate returnDate;

    private TicketCategory ticketCategory = TicketCategory.ONE_WAY;

    private ScheduleSaleCardDTO selectedOutboundSchedule;
    private ScheduleSaleCardDTO selectedReturnSchedule;

    private final List<SelectedSeatDraft> outboundSeats = new ArrayList<>();
    private final List<SelectedSeatDraft> returnSeats = new ArrayList<>();
    private final List<PassengerDraft> passengers = new ArrayList<>();
    private BuyerDraft buyer;
    private final List<SaleChildUnder6DTO> childrenUnder6 = new ArrayList<>();
    private boolean syncBuyerFromFirstPassenger = true;

    private String selectedCustomerId;
    private Integer rewardPoints;
    private Double previewSubtotal;

    // THÊM VÀO ĐẦU CLASS
    private boolean isExchangeMode = false;
    private java.util.List<vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO> exchangeOldTickets = new java.util.ArrayList<>();
    private vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO exchangePreviewDTO;

    // THÊM GETTER/SETTER
    public boolean isExchangeMode() {
        return isExchangeMode;
    }

    public void setExchangeMode(boolean exchangeMode) {
        this.isExchangeMode = exchangeMode;
    }

    public java.util.List<vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO> getExchangeOldTickets() {
        return exchangeOldTickets;
    }

    public void setExchangeOldTickets(
            java.util.List<vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO> exchangeOldTickets) {
        this.exchangeOldTickets = exchangeOldTickets;
    }

    public vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO getExchangePreviewDTO() {
        return exchangePreviewDTO;
    }

    public void setExchangePreviewDTO(vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO exchangePreviewDTO) {
        this.exchangePreviewDTO = exchangePreviewDTO;
    }

    public String getClientSessionId() {
        return clientSessionId;
    }

    public StationDTO getDepartureStation() {
        return departureStation;
    }

    public void setDepartureStation(StationDTO departureStation) {
        this.departureStation = departureStation;
    }

    public StationDTO getDestinationStation() {
        return destinationStation;
    }

    public void setDestinationStation(StationDTO destinationStation) {
        this.destinationStation = destinationStation;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public TicketCategory getTicketCategory() {
        return ticketCategory;
    }

    public void setTicketCategory(TicketCategory ticketCategory) {
        this.ticketCategory = ticketCategory;
    }

    public ScheduleSaleCardDTO getSelectedOutboundSchedule() {
        return selectedOutboundSchedule;
    }

    public void setSelectedOutboundSchedule(ScheduleSaleCardDTO selectedOutboundSchedule) {
        this.selectedOutboundSchedule = selectedOutboundSchedule;
    }

    public ScheduleSaleCardDTO getSelectedReturnSchedule() {
        return selectedReturnSchedule;
    }

    public void setSelectedReturnSchedule(ScheduleSaleCardDTO selectedReturnSchedule) {
        this.selectedReturnSchedule = selectedReturnSchedule;
    }

    public List<SelectedSeatDraft> getOutboundSeats() {
        return outboundSeats;
    }

    public List<SelectedSeatDraft> getReturnSeats() {
        return returnSeats;
    }

    public List<PassengerDraft> getPassengers() {
        return passengers;
    }

    public BuyerDraft getBuyer() {
        return buyer;
    }

    public void setBuyer(BuyerDraft buyer) {
        this.buyer = buyer;
    }

    public List<SaleChildUnder6DTO> getChildrenUnder6() {
        return childrenUnder6;
    }

    public boolean isSyncBuyerFromFirstPassenger() {
        return syncBuyerFromFirstPassenger;
    }

    public void setSyncBuyerFromFirstPassenger(boolean syncBuyerFromFirstPassenger) {
        this.syncBuyerFromFirstPassenger = syncBuyerFromFirstPassenger;
    }

    public String getSelectedCustomerId() {
        return selectedCustomerId;
    }

    public void setSelectedCustomerId(String selectedCustomerId) {
        this.selectedCustomerId = selectedCustomerId;
    }

    public Integer getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Integer rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public Double getPreviewSubtotal() {
        return previewSubtotal;
    }

    public void setPreviewSubtotal(Double previewSubtotal) {
        this.previewSubtotal = previewSubtotal;
    }

    public static class SelectedSeatDraft {
        private TripDirection direction;
        private String scheduleId;
        private String scheduleDetailId;

        private String carriageId;
        private Integer carriageNumber;

        private String seatId;
        private Integer seatNumber;
        private SeatType seatType;
        private Double price;

        private Long holdExpiresAtEpochMillis;

        public SelectedSeatDraft() {
        }

        public SelectedSeatDraft(TripDirection direction, String scheduleId, String scheduleDetailId) {
            this.direction = direction;
            this.scheduleId = scheduleId;
            this.scheduleDetailId = scheduleDetailId;
        }

        public TripDirection getDirection() {
            return direction;
        }

        public void setDirection(TripDirection direction) {
            this.direction = direction;
        }

        public String getScheduleId() {
            return scheduleId;
        }

        public void setScheduleId(String scheduleId) {
            this.scheduleId = scheduleId;
        }

        public String getScheduleDetailId() {
            return scheduleDetailId;
        }

        public void setScheduleDetailId(String scheduleDetailId) {
            this.scheduleDetailId = scheduleDetailId;
        }

        public String getCarriageId() {
            return carriageId;
        }

        public void setCarriageId(String carriageId) {
            this.carriageId = carriageId;
        }

        public Integer getCarriageNumber() {
            return carriageNumber;
        }

        public void setCarriageNumber(Integer carriageNumber) {
            this.carriageNumber = carriageNumber;
        }

        public String getSeatId() {
            return seatId;
        }

        public void setSeatId(String seatId) {
            this.seatId = seatId;
        }

        public Integer getSeatNumber() {
            return seatNumber;
        }

        public void setSeatNumber(Integer seatNumber) {
            this.seatNumber = seatNumber;
        }

        public SeatType getSeatType() {
            return seatType;
        }

        public void setSeatType(SeatType seatType) {
            this.seatType = seatType;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public Long getHoldExpiresAtEpochMillis() {
            return holdExpiresAtEpochMillis;
        }

        public void setHoldExpiresAtEpochMillis(Long holdExpiresAtEpochMillis) {
            this.holdExpiresAtEpochMillis = holdExpiresAtEpochMillis;
        }
    }

    public static class PassengerDraft {
        private String fullName;
        private DocumentType documentType;
        private String documentNumber;
        private TicketType ticketType = TicketType.NORMAL;
        private LocalDate dateOfBirth;
        private boolean studentCardVerified;
        private String adultTicketCode;

        private boolean hasSeat = true;
        private boolean childUnder6;
        private boolean seatsReleased;

        private SelectedSeatDraft outboundSeat;
        private SelectedSeatDraft returnSeat;

        private double previewTotal;

        public PassengerDraft() {
        }

        public PassengerDraft(String fullName, String documentNumber) {
            this.fullName = fullName;
            this.documentNumber = documentNumber;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDocumentNumber() {
            return documentNumber;
        }

        public void setDocumentNumber(String documentNumber) {
            this.documentNumber = documentNumber;
        }

        public DocumentType getDocumentType() {
            return documentType;
        }

        public void setDocumentType(DocumentType documentType) {
            this.documentType = documentType;
        }

        public TicketType getTicketType() {
            return ticketType;
        }

        public void setTicketType(TicketType ticketType) {
            this.ticketType = ticketType;
        }

        public LocalDate getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public boolean isStudentCardVerified() {
            return studentCardVerified;
        }

        public void setStudentCardVerified(boolean studentCardVerified) {
            this.studentCardVerified = studentCardVerified;
        }

        public String getAdultTicketCode() {
            return adultTicketCode;
        }

        public void setAdultTicketCode(String adultTicketCode) {
            this.adultTicketCode = adultTicketCode;
        }

        public boolean isHasSeat() {
            return hasSeat;
        }

        public void setHasSeat(boolean hasSeat) {
            this.hasSeat = hasSeat;
        }

        public boolean isChildUnder6() {
            return childUnder6;
        }

        public void setChildUnder6(boolean childUnder6) {
            this.childUnder6 = childUnder6;
        }

        public boolean isSeatsReleased() {
            return seatsReleased;
        }

        public void setSeatsReleased(boolean seatsReleased) {
            this.seatsReleased = seatsReleased;
        }

        public SelectedSeatDraft getOutboundSeat() {
            return outboundSeat;
        }

        public void setOutboundSeat(SelectedSeatDraft outboundSeat) {
            this.outboundSeat = outboundSeat;
        }

        public SelectedSeatDraft getReturnSeat() {
            return returnSeat;
        }

        public void setReturnSeat(SelectedSeatDraft returnSeat) {
            this.returnSeat = returnSeat;
        }

        public double getPreviewTotal() {
            return previewTotal;
        }

        public void setPreviewTotal(double previewTotal) {
            this.previewTotal = previewTotal;
        }
    }

    public static class BuyerDraft {
        private String fullName;
        private DocumentType documentType;
        private String documentNumber;
        private String email;
        private String phoneNumber;
        private String customerId;
        private boolean hasAccount;

        public BuyerDraft() {
        }

        public BuyerDraft(String fullName, String documentNumber, String email, String phoneNumber) {
            this.fullName = fullName;
            this.documentNumber = documentNumber;
            this.email = email;
            this.phoneNumber = phoneNumber;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDocumentNumber() {
            return documentNumber;
        }

        public void setDocumentNumber(String documentNumber) {
            this.documentNumber = documentNumber;
        }

        public DocumentType getDocumentType() {
            return documentType;
        }

        public void setDocumentType(DocumentType documentType) {
            this.documentType = documentType;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public boolean isHasAccount() {
            return hasAccount;
        }

        public void setHasAccount(boolean hasAccount) {
            this.hasAccount = hasAccount;
        }
    }
}
