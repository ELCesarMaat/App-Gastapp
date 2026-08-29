using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class UpdateCreditCardAndSpendingFields : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "CurrentInstallment",
                table: "Spendings",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<decimal>(
                name: "InstallmentMonthlyAmount",
                table: "Spendings",
                type: "numeric",
                nullable: false,
                defaultValue: 0m);

            migrationBuilder.AddColumn<bool>(
                name: "IsMsi",
                table: "Spendings",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "ParentSpendingId",
                table: "Spendings",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "PaymentMethod",
                table: "Spendings",
                type: "text",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<int>(
                name: "TotalInstallments",
                table: "Spendings",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<string>(
                name: "ColorHex",
                table: "CreditCards",
                type: "text",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<decimal>(
                name: "CreditLimit",
                table: "CreditCards",
                type: "numeric",
                nullable: false,
                defaultValue: 0m);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "CurrentInstallment",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "InstallmentMonthlyAmount",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "IsMsi",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "ParentSpendingId",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "PaymentMethod",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "TotalInstallments",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "ColorHex",
                table: "CreditCards");

            migrationBuilder.DropColumn(
                name: "CreditLimit",
                table: "CreditCards");
        }
    }
}
