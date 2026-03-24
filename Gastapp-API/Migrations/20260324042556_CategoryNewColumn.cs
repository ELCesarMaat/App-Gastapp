using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class CategoryNewColumn : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsDefaultCategory",
                table: "Categories",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "IsDefaultCategory",
                table: "Categories");
        }
    }
}
